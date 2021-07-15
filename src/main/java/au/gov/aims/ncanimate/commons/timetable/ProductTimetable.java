/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.timetable;

import au.gov.aims.ereefs.bean.metadata.TimeIncrement;
import au.gov.aims.ereefs.bean.metadata.TimeIncrementUnit;
import au.gov.aims.ereefs.bean.metadata.ncanimate.NcAnimateOutputFileMetadataBean;
import au.gov.aims.ereefs.bean.metadata.netcdf.NetCDFMetadataBean;
import au.gov.aims.ereefs.bean.metadata.netcdf.TemporalDomainBean;
import au.gov.aims.ereefs.bean.metadata.netcdf.VariableMetadataBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateConfigBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateIdBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateInputBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateLayerBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateNetCDFVariableBean;
import au.gov.aims.ereefs.bean.ncanimate.render.AbstractNcAnimateRenderFileBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderMapBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderVideoBean;
import au.gov.aims.ereefs.database.CacheStrategy;
import au.gov.aims.ereefs.database.DatabaseClient;
import au.gov.aims.ereefs.helper.MetadataHelper;
import au.gov.aims.ereefs.helper.NcAnimateConfigHelper;
import au.gov.aims.ncanimate.commons.NcAnimateGenerateFileBean;
import au.gov.aims.ncanimate.commons.NcAnimateUtils;
import au.gov.aims.ncanimate.commons.generator.context.GeneratorContext;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Map of timetable per panel's NetCDF layer.
 *
 * ProductTimetable (Map):
 *     Key: Product file date range (the date range for a map or video)
 *     Value: List of FrameTimetableMap (Map):
 *         Key: DateTimeRange
 *         Value: FrameTimetable (Map):
 *             Key: layerId (String)
 *             Value: NetCDFMetadataSet
 *                 NetCDFMetadataSet: Set of metadata frame (NetCDFMetadataBean + DateTime) ordered by most suitable file
 *                     according to its temporal domain; file containing most recent data appear first.
 * NOTE: We need a set of metadata (in case of multiple NetCDF file match for a variable & timestamp).
 *     When NcAnimate generate the NetCDF raster layer, it iterate through the list and pick the first one
 *     which have data available.
 */
public class ProductTimetable {
    private static final Logger LOGGER = Logger.getLogger(ProductTimetable.class);

    private DatabaseClient dbClient;
    private NcAnimateConfigBean ncAnimateConfig;

    private TimeIncrement mapTimeIncrement;
    private TreeMap<DateTimeRange, List<FrameTimetableMap>> mapFrames;

    private TimeIncrement videoTimeIncrement;
    private TreeMap<DateTimeRange, List<FrameTimetableMap>> videoFrames;

    public ProductTimetable(NcAnimateConfigBean ncAnimateConfig, DatabaseClient dbClient) throws Exception {
        this.mapFrames = new TreeMap<DateTimeRange, List<FrameTimetableMap>>();
        this.videoFrames = new TreeMap<DateTimeRange, List<FrameTimetableMap>>();

        this.dbClient = dbClient;
        this.ncAnimateConfig = ncAnimateConfig;

        this.init();
    }

    public TimeIncrement getMapTimeIncrement() {
        return this.mapTimeIncrement;
    }

    public TimeIncrement getVideoTimeIncrement() {
        return this.videoTimeIncrement;
    }

    private void init() throws Exception {
        NcAnimateRenderBean render = this.ncAnimateConfig.getRender();

        if (render != null) {
            DateTimeZone timezone = render.getDateTimeZone();

            Map<String, NcAnimateRenderMapBean> maps = render.getMaps();
            Map<String, NcAnimateRenderVideoBean> videos = render.getVideos();

            boolean hasMaps = maps != null && !maps.isEmpty();
            boolean hasVideos = videos != null && !videos.isEmpty();

            this.mapTimeIncrement = hasMaps ? this.ncAnimateConfig.getFrameTimeIncrement() : null;

            this.videoTimeIncrement = null;
            if (hasVideos) {
                this.videoTimeIncrement = render.getVideoTimeIncrement();
                if (this.videoTimeIncrement == null) {
                    this.videoTimeIncrement = this.ncAnimateConfig.getFrameTimeIncrement();
                }
            }

            // Get startDate and endDate from config and/or input files
            DateTime startDate = null, endDate, maxEndDate = null;

            if (render.getStartDate() != null) {
                startDate = DateTime.parse(render.getStartDate()).withZone(timezone);
            }

            if (render.getEndDate() != null) {
                maxEndDate = DateTime.parse(render.getEndDate()).withZone(timezone);
            }

            // Create a set of definition ID used by focus layers,
            // to figure out which definition should be considered
            // in the ProductTimetable
            Set<String> focusDefinitionIdSet = new HashSet<String>();
            Set<String> focusLayerIdSet = new HashSet<String>();
            List<String> focusLayerIds = this.ncAnimateConfig.getFocusLayers();
            if (focusLayerIds != null && !focusLayerIds.isEmpty()) {
                focusLayerIdSet.addAll(focusLayerIds);
                Map<String, NcAnimateLayerBean> layerMap = NcAnimateUtils.getLayers(this.ncAnimateConfig);
                for (String focusLayerId : focusLayerIds) {
                    NcAnimateLayerBean layer = layerMap.get(focusLayerId);
                    NcAnimateInputBean input = layer == null ? null : layer.getInput();
                    NcAnimateIdBean inputId = input == null ? null : input.getId();

                    if (inputId == null) {
                        LOGGER.warn(String.format("Invalid focus layer ID: %s", focusLayerId));
                    } else {
                        focusDefinitionIdSet.add(inputId.getValue());
                    }
                }
            }

            // If startDate is not specified in config,
            // get date of the beginning of the year of the first date from the input NetCDF files.
            DateTimeRange inputDateTimeRange = this.getInputDateTimeRange(focusDefinitionIdSet);
            if (inputDateTimeRange != null) {
                TimeIncrement frameTimeIncrement = this.ncAnimateConfig.getFrameTimeIncrement();
                boolean isFrameEternity = TimeIncrementUnit.ETERNITY.equals(frameTimeIncrement.getUnit());

                DateTime firstDate = inputDateTimeRange.getStartDate().withZone(timezone);
                if (startDate == null) {
                    // Align the date with timestamps found in data
                    DateTime idealStartDate = new DateTime(firstDate.getYear(), 1, 1, 0, 0, timezone);
                    startDate = firstDate;
                    DateTime previousStartDate = firstDate;

                    if (!isFrameEternity) {
                        while (previousStartDate.compareTo(idealStartDate) >= 0) {
                            startDate = previousStartDate;
                            previousStartDate = startDate.minus(frameTimeIncrement.getPeriod());
                        }
                    }
                }

                // If endDate is specified in config,
                // be sure to not go beyond that date.
                DateTime inputEndDate = inputDateTimeRange.getEndDate().withZone(timezone);
                if (isFrameEternity) {
                    endDate = inputEndDate;
                } else {
                    endDate = this.nextDateTime(inputEndDate, null, frameTimeIncrement);
                    if (endDate != null && maxEndDate != null && endDate.compareTo(maxEndDate) > 0) {
                        endDate = maxEndDate;
                    }
                }

                // The only way startDate or endDate can be null is if the dates are not specified in config and
                //     there is no NetCDF with time axis defined in config.
                //     => If it's the case, there is nothing worth generating
                // If the data available is out of range for the provided startDate and endDate, that will produce
                //     an invalid date range (end date before start date).
                //     => If it's the case, there is nothing worth generating
                if (startDate != null && endDate != null && startDate.compareTo(endDate) < 0) {
                    // Fix date timezone
                    startDate = startDate.withZone(timezone);
                    endDate = endDate.withZone(timezone);

                    LOGGER.debug(String.format("Product ID: %s startDate: %s endDate: %s",
                            this.ncAnimateConfig.getId().getValue(), startDate, endDate));

                    if (hasMaps) {
                        boolean isMapEternity = TimeIncrementUnit.ETERNITY.equals(this.mapTimeIncrement.getUnit());

                        if (isMapEternity) {
                            DateTimeRange mapFileDateTimeRange = DateTimeRange.ALL_TIME;
                            this.addMapFrameTimetable(mapFileDateTimeRange, new FrameTimetableMap(this.ncAnimateConfig, mapFileDateTimeRange, this.dbClient), focusLayerIdSet);
                        } else {
                            DateTimeRange mapFileDateTimeRange = DateTimeRange.create(startDate, this.nextDateTime(startDate, endDate, this.mapTimeIncrement));

                            do {
                                if (firstDate.compareTo(mapFileDateTimeRange.getEndDate()) < 0) {
                                    this.addMapFrameTimetable(mapFileDateTimeRange, new FrameTimetableMap(this.ncAnimateConfig, mapFileDateTimeRange, this.dbClient), focusLayerIdSet);
                                }

                                mapFileDateTimeRange = mapFileDateTimeRange.next(this.mapTimeIncrement);
                                if (mapFileDateTimeRange.getEndDate().compareTo(endDate) > 0) {
                                    mapFileDateTimeRange = DateTimeRange.create(mapFileDateTimeRange.getStartDate(), endDate);
                                }
                            } while (mapFileDateTimeRange.getStartDate().compareTo(endDate) < 0);
                        }
                    }

                    if (hasVideos) {
                        boolean isVideoEternity = TimeIncrementUnit.ETERNITY.equals(this.videoTimeIncrement.getUnit());

                        if (isVideoEternity) {
                            DateTimeRange videoFileDateTimeRange = DateTimeRange.ALL_TIME;
                            this.addVideoFrameTimetable(videoFileDateTimeRange, new FrameTimetableMap(this.ncAnimateConfig, videoFileDateTimeRange, this.dbClient), focusLayerIdSet);
                        } else {
                            DateTimeRange videoFileDateTimeRange = DateTimeRange.create(startDate, this.nextDateTime(startDate, endDate, this.videoTimeIncrement));
                            do {
                                if (firstDate.compareTo(videoFileDateTimeRange.getEndDate()) < 0) {
                                    DateTime fixedStartDate = videoFileDateTimeRange.getStartDate();
                                    DateTime fixedEndDate = videoFileDateTimeRange.getEndDate();

                                    if (firstDate.compareTo(fixedStartDate) > 0) {
                                        fixedStartDate = firstDate;
                                    }
                                    if (endDate.compareTo(fixedEndDate) < 0) {
                                        fixedEndDate = endDate;
                                    }

                                    DateTimeRange fixedVideoFileDateTimeRange = DateTimeRange.create(fixedStartDate, fixedEndDate);

                                    this.addVideoFrameTimetable(fixedVideoFileDateTimeRange, new FrameTimetableMap(this.ncAnimateConfig, fixedVideoFileDateTimeRange, this.dbClient), focusLayerIdSet);
                                }

                                videoFileDateTimeRange = videoFileDateTimeRange.next(this.videoTimeIncrement);
                                if (videoFileDateTimeRange.getEndDate().compareTo(endDate) > 0) {
                                    videoFileDateTimeRange = DateTimeRange.create(videoFileDateTimeRange.getStartDate(), endDate);
                                }
                            } while (videoFileDateTimeRange.getStartDate().compareTo(endDate) < 0);
                        }
                    }
                }
            }
        }
    }

    private DateTime nextDateTime(DateTime startDate, DateTime endDate, TimeIncrement timeIncrement) {
        DateTime nextDate = startDate == null ? null : startDate.plus(timeIncrement.getPeriod());

        if (nextDate == null) {
            return endDate;
        }

        if (endDate != null && nextDate.compareTo(endDate) > 0) {
            return endDate;
        }

        return nextDate;
    }

    private void addMapFrameTimetable(DateTimeRange mapFileDateTimeRange, FrameTimetableMap mapFrameTimetable, Set<String> focusLayerIdSet) {
        // Ignore maps that doesn't contains data
        if (this.acceptProductFrameTimetable(mapFrameTimetable, focusLayerIdSet)) {
            List<FrameTimetableMap> mapFrameTimetableList = this.mapFrames.get(mapFileDateTimeRange);
            if (mapFrameTimetableList == null) {
                mapFrameTimetableList = new ArrayList<FrameTimetableMap>();
                this.mapFrames.put(mapFileDateTimeRange, mapFrameTimetableList);
            }

            mapFrameTimetableList.add(mapFrameTimetable);
        }
    }

    private void addVideoFrameTimetable(DateTimeRange videoFileDateTimeRange, FrameTimetableMap videoFrameTimetable, Set<String> focusLayerIdSet) {
        // Ignore videos that has no frame containing data
        if (this.acceptProductFrameTimetable(videoFrameTimetable, focusLayerIdSet)) {
            List<FrameTimetableMap> videoFrameTimetableList = this.videoFrames.get(videoFileDateTimeRange);
            if (videoFrameTimetableList == null) {
                videoFrameTimetableList = new ArrayList<FrameTimetableMap>();
                this.videoFrames.put(videoFileDateTimeRange, videoFrameTimetableList);
            }

            videoFrameTimetableList.add(videoFrameTimetable);
        }
    }

    private boolean acceptProductFrameTimetable(FrameTimetableMap productFrameTimetable, Set<String> focusLayerIdSet) {
        if (productFrameTimetable == null || productFrameTimetable.isEmpty()) {
            // The product is empty
            return false;
        }

        if (focusLayerIdSet == null || focusLayerIdSet.isEmpty()) {
            // There is no focus layer, therefore all layers are on focus
            return true;
        }

        for (FrameTimetable frameTimetable : productFrameTimetable.values()) {
            for (String layerId : frameTimetable.keySet()) {
                if (focusLayerIdSet.contains(layerId)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Find the min & max date of all the NetCDF files used by this product.
     * @return
     */
    private DateTimeRange getInputDateTimeRange(Set<String> focusDefinitionIdSet) throws Exception {
        DateTime absoluteMinDate = null, absoluteMaxDate = null;
        Map<String, Map<String, NetCDFMetadataBean>> netCDFMetadataMap =
                NcAnimateConfigHelper.getValidNetCDFMetadataMap(this.ncAnimateConfig, new MetadataHelper(this.dbClient, CacheStrategy.DISK));

        if (netCDFMetadataMap != null) {
            for (Map.Entry<String, Map<String, NetCDFMetadataBean>> netCDFMetadataEntry : netCDFMetadataMap.entrySet()) {
                String definitionId = netCDFMetadataEntry.getKey();
                // Check if the metadata should be considered (aka is used by focus layers)
                boolean selected = focusDefinitionIdSet.isEmpty() || focusDefinitionIdSet.contains(definitionId);
                if (selected) {
                    Map<String, NetCDFMetadataBean> netCDFMetadatas = netCDFMetadataEntry.getValue();
                    if (netCDFMetadatas != null) {
                        for (NetCDFMetadataBean metadata : netCDFMetadatas.values()) {
                            Set<NcAnimateNetCDFVariableBean> variables = NcAnimateConfigHelper.getUsedTemporalVariables(this.ncAnimateConfig, metadata);
                            if (variables != null) {
                                for (NcAnimateNetCDFVariableBean variable : variables) {
                                    VariableMetadataBean variableMetadata = NcAnimateConfigHelper.getVariableMetadata(metadata, variable);
                                    if (variableMetadata != null) {
                                        TemporalDomainBean temporalDomain = variableMetadata.getTemporalDomainBean();
                                        if (temporalDomain != null) {
                                            DateTime minDate = temporalDomain.getMinDate();
                                            DateTime maxDate = temporalDomain.getMaxDate();

                                            if (absoluteMinDate == null || absoluteMinDate.compareTo(minDate) > 0) {
                                                absoluteMinDate = minDate;
                                            }
                                            if (absoluteMaxDate == null || absoluteMaxDate.compareTo(maxDate) < 0) {
                                                absoluteMaxDate = maxDate;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (absoluteMinDate != null && absoluteMaxDate != null) {
            return DateTimeRange.create(absoluteMinDate, absoluteMaxDate);
        }
        return null;
    }

    /**
     * Get a list of output files that will be generated from the list of frames
     * @return
     */
    public List<NcAnimateGenerateFileBean> getVideoOutputFiles() throws Exception {
        List<NcAnimateGenerateFileBean> videoOutputFiles = new ArrayList<NcAnimateGenerateFileBean>();

        if (this.videoTimeIncrement != null) {
            NcAnimateRenderBean render = this.ncAnimateConfig.getRender();

            if (render != null) {
                Map<String, NcAnimateRenderVideoBean> videos = render.getVideos();
                if (videos != null) {
                    this.addAllOutputFiles(videoOutputFiles, this.videoTimeIncrement,
                            String.format("%s_video", this.ncAnimateConfig.getId().getValue()),
                            this.videoFrames, videos);
                }
            }
        }

        return videoOutputFiles;
    }

    public List<NcAnimateGenerateFileBean> getMapOutputFiles() throws Exception {
        List<NcAnimateGenerateFileBean> mapOutputFiles = new ArrayList<NcAnimateGenerateFileBean>();

        if (this.mapTimeIncrement != null) {
            NcAnimateRenderBean render = this.ncAnimateConfig.getRender();

            if (render != null) {
                Map<String, NcAnimateRenderMapBean> maps = render.getMaps();
                if (maps != null) {
                    this.addAllOutputFiles(mapOutputFiles, this.mapTimeIncrement,
                            String.format("%s_map", this.ncAnimateConfig.getId().getValue()),
                            this.mapFrames, maps);
                }
            }
        }

        return mapOutputFiles;
    }

    private void addAllOutputFiles(
            List<NcAnimateGenerateFileBean> outputFiles,
            TimeIncrement outputFileTimeIncrement,
            String fileIdPrefix,
            Map<DateTimeRange, List<FrameTimetableMap>> frameTimetable,
            Map<String, ? extends AbstractNcAnimateRenderFileBean> renderFiles
    ) throws Exception {

        if (outputFiles != null && frameTimetable != null && !frameTimetable.isEmpty()) {
            if (fileIdPrefix == null) {
                fileIdPrefix = "";
            }

            MetadataHelper metadataHelper = new MetadataHelper(this.dbClient, CacheStrategy.DISK);

            GeneratorContext context = new GeneratorContext(this.ncAnimateConfig);

            NcAnimateRenderBean render = this.ncAnimateConfig.getRender();
            if (render != null) {
                for (DateTimeRange outputFileDateRange : frameTimetable.keySet()) {
                    URI directoryUri = new URI(NcAnimateUtils.parseString(render.getDirectoryUri(), context));

                    String fileId = null;
                    DateTime startDate = outputFileDateRange.getStartDate();
                    TimeIncrementUnit outputFileTimeIncrementUnit = outputFileTimeIncrement.getSafeUnit();
                    switch (outputFileTimeIncrementUnit) {
                        case MINUTE:
                            fileId = String.format("%s_hourly_%04d-%02d-%02d_%02dh%02d", fileIdPrefix, startDate.getYear(), startDate.getMonthOfYear(), startDate.getDayOfMonth(), startDate.getHourOfDay(), startDate.getMinuteOfHour());
                            break;

                        case HOUR:
                            fileId = String.format("%s_hourly_%04d-%02d-%02d_%02dh00", fileIdPrefix, startDate.getYear(), startDate.getMonthOfYear(), startDate.getDayOfMonth(), startDate.getHourOfDay());
                            break;

                        case DAY:
                            fileId = String.format("%s_daily_%04d-%02d-%02d", fileIdPrefix, startDate.getYear(), startDate.getMonthOfYear(), startDate.getDayOfMonth());
                            break;

                        case MONTH:
                            fileId = String.format("%s_monthly_%04d-%02d", fileIdPrefix, startDate.getYear(), startDate.getMonthOfYear());
                            break;

                        case YEAR:
                            fileId = String.format("%s_yearly_%04d", fileIdPrefix, startDate.getYear());
                            break;

                        case ETERNITY:
                            fileId = String.format("%s_all", fileIdPrefix);
                            break;

                        default:
                            throw new IllegalArgumentException("Invalid output file time increment: " + outputFileTimeIncrement);
                    }

                    NcAnimateGenerateFileBean outputFile = this.getOutputFile(metadataHelper, directoryUri, fileId, renderFiles, outputFileDateRange);
                    outputFiles.add(outputFile);
                }
            }
        }
    }

    private NcAnimateGenerateFileBean getOutputFile(
            MetadataHelper metadataHelper,
            URI directoryUri,
            String fileId,
            Map<String, ? extends AbstractNcAnimateRenderFileBean> renderFiles,
            DateTimeRange dateRange
    ) throws Exception {

        String definitionId = null;
        NcAnimateRenderBean render = this.ncAnimateConfig.getRender();
        if (render != null) {
            definitionId = render.getDefinitionId();
        }
        if (definitionId == null) {
            definitionId = this.ncAnimateConfig.getId().getValue();
        }

        NcAnimateOutputFileMetadataBean outputFile = metadataHelper.getNcAnimateProductMetadata(definitionId, fileId);
        DateTime startDate = dateRange.getStartDate();
        DateTime endDate = dateRange.getEndDate();

        if (outputFile != null) {
            if (!dateTimeEquals(startDate, outputFile.getStartDate()) ||
                    !dateTimeEquals(endDate, outputFile.getEndDate())) {

                // The product dates have changed. Delete it so it can be re-generated.
                metadataHelper.deleteNcAnimateProductMetadata(definitionId, fileId);
            }
        }

        // Create a copy of the map of render files found in the configuration file (png, svg, etc).
        // The NcAnimateOutputFileMetadataBean constructor set the fileURI for each render files,
        // therefore they can not share the same instance.
        Map<String, AbstractNcAnimateRenderFileBean> renderFilesCopy = new HashMap<String, AbstractNcAnimateRenderFileBean>();
        if (renderFiles != null) {
            for (Map.Entry<String, ? extends AbstractNcAnimateRenderFileBean> renderFileEntry : renderFiles.entrySet()) {
                renderFilesCopy.put(
                    renderFileEntry.getKey(),
                    AbstractNcAnimateRenderFileBean.copyRenderFile(renderFileEntry.getValue())
                );
            }
        }

        return new NcAnimateGenerateFileBean(
            definitionId,
            directoryUri,
            fileId,
            renderFilesCopy,
            DateTimeRange.create(startDate, endDate)
        );
    }

    private boolean dateTimeEquals(DateTime date1, DateTime date2) {
        if (date1 == date2) {
            return true;
        }
        if (date1 == null || date2 == null) {
            return false;
        }
        return date1.equals(date2);
    }

    public TreeMap<DateTimeRange, List<FrameTimetableMap>> getMapFrames() {
        return this.mapFrames;
    }

    public TreeMap<DateTimeRange, List<FrameTimetableMap>> getVideoFrames() {
        return this.videoFrames;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        json.put("productId", this.ncAnimateConfig.getId().getValue());

        json.put("mapTimeIncrement", this.mapTimeIncrement == null ? null : this.mapTimeIncrement.toJSON());
        JSONArray jsonMapFrames = new JSONArray();
        if (this.mapFrames != null && !this.mapFrames.isEmpty()) {
            for (Map.Entry<DateTimeRange, List<FrameTimetableMap>> entry : this.mapFrames.entrySet()) {
                List<FrameTimetableMap> frames = entry.getValue();
                JSONArray jsonMapFrameList = new JSONArray();
                if (frames != null && !frames.isEmpty()) {
                    for (FrameTimetableMap frame : frames) {
                        jsonMapFrameList.put(frame.toJSON());
                    }
                }
                jsonMapFrames.put(new JSONObject()
                        .put("dates", entry.getKey().toString())
                        .put("mapFrames", jsonMapFrameList));
            }
        }
        json.put("mapFrames", jsonMapFrames);

        json.put("videoTimeIncrement", this.videoTimeIncrement == null ? null : this.videoTimeIncrement.toJSON());
        JSONArray jsonVideoFrames = new JSONArray();
        if (this.videoFrames != null && !this.videoFrames.isEmpty()) {
            for (Map.Entry<DateTimeRange, List<FrameTimetableMap>> entry : this.videoFrames.entrySet()) {
                List<FrameTimetableMap> frames = entry.getValue();
                JSONArray jsonVideoFrameList = new JSONArray();
                if (frames != null && !frames.isEmpty()) {
                    for (FrameTimetableMap frame : frames) {
                        jsonVideoFrameList.put(frame.toJSON());
                    }
                }
                jsonVideoFrames.put(new JSONObject()
                        .put("dates", entry.getKey().toString())
                        .put("videoFrames", jsonVideoFrameList));
            }
        }
        json.put("videoFrames", jsonVideoFrames);

        return json;
    }

    @Override
    public String toString() {
        return this.toJSON().toString(4);
    }
}
