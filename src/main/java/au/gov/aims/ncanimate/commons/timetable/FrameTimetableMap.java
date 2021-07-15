/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.timetable;

import au.gov.aims.ereefs.bean.metadata.TimeIncrement;
import au.gov.aims.ereefs.bean.metadata.netcdf.NetCDFMetadataBean;
import au.gov.aims.ereefs.bean.metadata.netcdf.TemporalDomainBean;
import au.gov.aims.ereefs.bean.metadata.netcdf.VariableMetadataBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateConfigBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateInputBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateLayerBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateNetCDFVariableBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimatePanelBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderBean;
import au.gov.aims.ereefs.database.CacheStrategy;
import au.gov.aims.ereefs.database.DatabaseClient;
import au.gov.aims.ereefs.helper.MetadataHelper;
import au.gov.aims.ereefs.helper.NcAnimateConfigHelper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Map of timetable per panel's NetCDF layer.
 *
 * FrameTimetableMap (Map):
 *     Key: DateTimeRange
 *     Value: FrameTimetable (Map):
 *         Key: layerId (String)
 *         Value: NetCDFMetadataSet
 *             NetCDFMetadataSet: Set of metadata frame (NetCDFMetadataBean + DateTime) ordered by most suitable file
 *                 according to its temporal domain; file containing most recent data appear first.
 * NOTE: We need a set of metadata (in case of multiple NetCDF file match for a variable & timestamp).
 *     When NcAnimate generate the NetCDF raster layer, it iterate through the list and pick the first one
 *     which have data available.
 */
public class FrameTimetableMap extends TreeMap<DateTimeRange, FrameTimetable> {
    private DatabaseClient dbClient;
    private DateTimeRange coveredDateRange;

    public FrameTimetableMap(NcAnimateConfigBean ncAnimateConfig, DateTimeRange coveredDateRange, DatabaseClient dbClient) throws Exception {
        super();

        this.coveredDateRange = coveredDateRange;
        this.dbClient = dbClient;

        this.initTimetableMap(ncAnimateConfig);
    }

    private void initTimetableMap(NcAnimateConfigBean ncAnimateConfig) throws Exception {
        NcAnimateRenderBean render = ncAnimateConfig.getRender();
        if (render != null) {
            DateTimeZone timezone = render.getDateTimeZone();

            // Create a map of empty FrameTimetable
            if (DateTimeRange.ALL_TIME.equals(this.coveredDateRange)) {
                this.put(DateTimeRange.ALL_TIME, new FrameTimetable());
            } else {
                DateTime startDate = this.coveredDateRange.getStartDate().withZone(timezone);
                DateTime endDate = this.coveredDateRange.getEndDate().withZone(timezone);

                DateTimeRange currentDateRange = DateTimeRange.getDateTimeRange(startDate, ncAnimateConfig.getFrameTimeIncrement());

                // Calculate the list of date range that will be in the timetable
                // I.E. Create the list of dates for the frame that will be generated
                do {
                    this.put(currentDateRange, new FrameTimetable());
                    currentDateRange = currentDateRange.next(ncAnimateConfig.getFrameTimeIncrement());
                } while (currentDateRange.getEndDate().compareTo(endDate) <= 0);
            }

            // Fill the FrameTimetables with links to available data
            List<NcAnimatePanelBean> panels = ncAnimateConfig.getPanels();
            if (panels != null) {

                Map<String, Map<String, NetCDFMetadataBean>> netCDFMetadataMap =
                    NcAnimateConfigHelper.getValidNetCDFMetadataMap(
                        ncAnimateConfig, new MetadataHelper(this.dbClient, CacheStrategy.DISK));

                if (netCDFMetadataMap != null && !netCDFMetadataMap.isEmpty()) {
                    for (NcAnimatePanelBean panel : panels) {
                        List<NcAnimateLayerBean> layers = panel.getLayers();
                        if (layers != null) {
                            for (NcAnimateLayerBean layer : layers) {
                                NcAnimateInputBean input = layer.getInput();
                                if (input != null) {
                                    String inputDefinitionId = input.getId().getValue();
                                    this.parseInput(layer, netCDFMetadataMap.get(inputDefinitionId), input.getTimeIncrement(), timezone);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean isEmpty() {
        if (super.isEmpty()) {
            return true;
        }

        for (FrameTimetable frameTimetable : this.values()) {
            if (!frameTimetable.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compute layer's timetable and the last modified timestamp from all input files.
     * The panel's timetable is used to find which NetCDF file contains data
     * for a given variable and a given timestamp.
     * @param layer
     * @param netCDFMetadataMap
     */
    private void parseInput(NcAnimateLayerBean layer, Map<String, NetCDFMetadataBean> netCDFMetadataMap, TimeIncrement inputFileTimeIncrement, DateTimeZone timezone) {
        if (netCDFMetadataMap != null) {
            for (NetCDFMetadataBean fileMetadata : netCDFMetadataMap.values()) {
                if (fileMetadata != null) {
                    String layerId = layer.getId().getValue();
                    NcAnimateNetCDFVariableBean variable = NcAnimateConfigHelper.getMostSignificantVariable(layer);
                    VariableMetadataBean variableMetadata = NcAnimateConfigHelper.getVariableMetadata(fileMetadata, variable);

                    if (variableMetadata != null) {
                        TemporalDomainBean temporalDomain = variableMetadata.getTemporalDomainBean();
                        if (temporalDomain != null) {
                            List<DateTime> times = temporalDomain.getTimeValues();
                            if (times != null && !times.isEmpty()) {
                                for (DateTime time : times) {
                                    time = time.withZone(timezone);
                                    DateTimeRange inputFileDateTimeRange = DateTimeRange.getDateTimeRange(time, inputFileTimeIncrement);
                                    if (inputFileDateTimeRange != null) {
                                        this.fitLast(layerId, inputFileDateTimeRange, new NetCDFMetadataFrame(time, fileMetadata, variableMetadata));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Put the netCDFMetadataFrame (file metadata + frame date) in the appropriate Timetable frame (date range)
    // Match first date
    // Select the frame from the input file that contains the video frame start date
    // Video frames shows the first data that intersect with video frame period
    private void fitFirst(String layerId, DateTimeRange inputFileDateTimeRange, NetCDFMetadataFrame netCDFMetadataFrame) {
        for (Map.Entry<DateTimeRange, FrameTimetable> frameTimetableEntry : this.entrySet()) {
            DateTimeRange dateTimeRange = frameTimetableEntry.getKey();
            FrameTimetable frameTimetable = frameTimetableEntry.getValue();

            if (DateTimeRange.ALL_TIME.equals(inputFileDateTimeRange)) {
                frameTimetable.add(layerId, netCDFMetadataFrame);
            }

            if (inputFileDateTimeRange.getStartDate() != null && inputFileDateTimeRange.getEndDate() != null) {
                DateTime startDate = dateTimeRange.getStartDate();
                // If the startDate of the time table frame is in input file frame (inputFileDateTimeRange)
                if (startDate.compareTo(inputFileDateTimeRange.getStartDate()) >= 0 && startDate.compareTo(inputFileDateTimeRange.getEndDate()) < 0) {
                    frameTimetable.add(layerId, netCDFMetadataFrame);
                }
            }
        }
    }

    // Match last date
    // Select the frame from the input file that contains the video frame end date
    // Video frames shows the most recent data for the video frame period
    private void fitLast(String layerId, DateTimeRange inputFileDateTimeRange, NetCDFMetadataFrame netCDFMetadataFrame) {
        for (Map.Entry<DateTimeRange, FrameTimetable> frameTimetableEntry : this.entrySet()) {
            DateTimeRange dateTimeRange = frameTimetableEntry.getKey();
            FrameTimetable frameTimetable = frameTimetableEntry.getValue();

            if (DateTimeRange.ALL_TIME.equals(inputFileDateTimeRange)) {
                frameTimetable.add(layerId, netCDFMetadataFrame);
            }

            if (inputFileDateTimeRange.getStartDate() != null && inputFileDateTimeRange.getEndDate() != null) {
                DateTime endDate = dateTimeRange.getEndDate();
                // If the endDate of the time table frame is in input file frame (inputFileDateTimeRange)
                if (endDate.compareTo(inputFileDateTimeRange.getEndDate()) <= 0 && endDate.compareTo(inputFileDateTimeRange.getStartDate()) > 0) {
                    frameTimetable.add(layerId, netCDFMetadataFrame);
                }
            }
        }
    }

    public Map.Entry<NetCDFMetadataBean, Long> getInputLastModifiedEntry() {
        Map<NetCDFMetadataBean, Long> inputLastModifiedMap = this.getInputLastModifiedMap();
        Map.Entry<NetCDFMetadataBean, Long> inputLastModifiedEntry = null;
        for (Map.Entry<NetCDFMetadataBean, Long> lastModifiedEntry : inputLastModifiedMap.entrySet()) {
            if (inputLastModifiedEntry == null) {
                inputLastModifiedEntry = lastModifiedEntry;
            } else if (lastModifiedEntry != null && lastModifiedEntry.getValue() > inputLastModifiedEntry.getValue()) {
                inputLastModifiedEntry = lastModifiedEntry;
            }
        }
        return inputLastModifiedEntry;
    }

    public Map<NetCDFMetadataBean, Long> getInputLastModifiedMap() {
        Map<NetCDFMetadataBean, Long> inputLastModifiedMap = new HashMap<NetCDFMetadataBean, Long>();

        for (FrameTimetable frameTimetable : this.values()) {
            for (NetCDFMetadataSet netCDFMetadataSet : frameTimetable.values()) {
                NetCDFMetadataFrame netCDFMetadataFrame = netCDFMetadataSet.first();
                NetCDFMetadataBean netCDFMetadata = netCDFMetadataFrame.getMetadata();
                if (!inputLastModifiedMap.containsKey(netCDFMetadata)) {
                    long inputFileLastModified = netCDFMetadata.getLastModified();
                    inputLastModifiedMap.put(netCDFMetadata, inputFileLastModified);
                }
            }
        }

        return inputLastModifiedMap;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        JSONArray jsonMap = new JSONArray();
        for (Map.Entry<DateTimeRange, FrameTimetable> entry : this.entrySet()) {
            FrameTimetable frameTimetable = entry.getValue();
            if (frameTimetable != null) {
                jsonMap.put(new JSONObject()
                        .put("dates", entry.getKey().toString())
                        .put("frameTimetable", frameTimetable.toJSON()));

            }
        }
        json.put("frames", jsonMap);

        return json;
    }

    @Override
    public String toString() {
        return this.toJSON().toString(4);
    }
}
