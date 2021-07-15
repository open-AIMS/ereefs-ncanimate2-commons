/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons;

import au.gov.aims.aws.s3.FileWrapper;
import au.gov.aims.aws.s3.entity.S3Client;
import au.gov.aims.aws.s3.manager.DownloadManager;
import au.gov.aims.ereefs.Utils;
import au.gov.aims.ereefs.bean.metadata.TimeIncrement;
import au.gov.aims.ereefs.bean.metadata.TimeIncrementUnit;
import au.gov.aims.ereefs.bean.metadata.netcdf.NetCDFMetadataBean;
import au.gov.aims.ereefs.bean.ncanimate.AbstractNcAnimateBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateBboxBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateConfigBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateInputBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateLayerBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimatePanelBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateRegionBean;
import au.gov.aims.ereefs.bean.ncanimate.render.AbstractNcAnimateRenderFileBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateTextBean;
import au.gov.aims.ereefs.helper.MetadataHelper;
import au.gov.aims.ncanimate.commons.generator.context.GeneratorContext;
import au.gov.aims.ncanimate.commons.generator.context.LayerContext;
import au.gov.aims.ncanimate.commons.timetable.DateTimeRange;
import au.gov.aims.ncanimate.commons.timetable.FrameTimetableMap;
import com.amazonaws.services.s3.AmazonS3URI;
import org.apache.log4j.Logger;
import org.apache.sis.metadata.iso.extent.DefaultGeographicBoundingBox;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NcAnimateUtils {
    private static final Logger LOGGER = Logger.getLogger(NcAnimateUtils.class);
    public static final String APP_NAME = "ncAnimate";

    // Cache
    private static Map<String, NetCDFMetadataBean> netCDFMetadataCacheMap;
    private static Map<String, File> inputFileCacheMap; // Cache one file per "definitionId" (download source)

    public static BoundingBox convertBoundingBox(NcAnimateBboxBean bboxBean) {
        if (bboxBean == null) {
            return null;
        }

        return new BoundingBoxImpl(new DefaultGeographicBoundingBox(
                bboxBean.getWest(),
                bboxBean.getEast(),
                bboxBean.getSouth(),
                bboxBean.getNorth()));
    }

    public static String getTimeIncrementLabel(TimeIncrement timeIncrement) {
        if (timeIncrement == null) {
            return null;
        }

        TimeIncrementUnit unit = timeIncrement.getSafeUnit();
        switch(unit) {
            case MINUTE:
                return "Minute";

            case HOUR:
                return "Hourly";

            case DAY:
                return "Daily";

            case MONTH:
                return "Monthly";

            case YEAR:
                return "Yearly";

            case ETERNITY:
                return "Overall";

            default:
                return null;
        }
    }

    public static int getInt(Integer integer) {
        return NcAnimateUtils.getInt(integer, 0);
    }

    public static int getInt(Integer integer, int defaultValue) {
        return integer == null ? defaultValue : integer;
    }

    public static Integer scale(Integer value, float scale) {
        if (value == null) {
            return null;
        }
        return Math.round(value * scale);
    }
    public static Integer scale(int value, float scale) {
        return Math.round(value * scale);
    }

    public static Float scale(Float value, float scale) {
        if (value == null) {
            return null;
        }
        return value * scale;
    }
    public static float scale(float value, float scale) {
        return value * scale;
    }

    public static int getFontStyle(NcAnimateTextBean textConf) {
        return NcAnimateUtils.getFontStyle(textConf, false, false);
    }

    public static int getFontStyle(NcAnimateTextBean textConf,
            boolean defaultBold, boolean defaultItalic) {

        int style = Font.PLAIN;

        boolean bold = defaultBold;
        boolean italic = defaultItalic;

        if (textConf != null) {
            if (textConf.getBold() != null) {
                bold = textConf.getBold();
            }
            if (textConf.getItalic() != null) {
                italic = textConf.getItalic();
            }
        }

        if (bold) {
            style += Font.BOLD;
        }
        if (italic) {
            style += Font.ITALIC;
        }

        return style;
    }


    public static void clearCache() {
        if (netCDFMetadataCacheMap != null) {
            netCDFMetadataCacheMap.clear();
            netCDFMetadataCacheMap = null;
        }

        if (inputFileCacheMap != null) {
            for (File cachedFile : inputFileCacheMap.values()) {
                LOGGER.warn(String.format("[-] Deleting cached input files: %s", cachedFile));
                if (cachedFile != null && !cachedFile.delete()) {
                    LOGGER.warn(String.format("Could not delete cached input file: %s", cachedFile));
                }
            }
            inputFileCacheMap.clear();
            inputFileCacheMap = null;
        }
    }

    public static NetCDFMetadataBean getInputFileMetadata(MetadataHelper metadataHelper, String definitionId, String datasetId) throws Exception {
        String uniqueId = NetCDFMetadataBean.getUniqueDatasetId(definitionId, datasetId);

        if (netCDFMetadataCacheMap == null) {
            netCDFMetadataCacheMap = new HashMap<String, NetCDFMetadataBean>();
        }

        if (netCDFMetadataCacheMap.containsKey(uniqueId)) {
            return netCDFMetadataCacheMap.get(uniqueId);
        }

        NetCDFMetadataBean fileMetadata = metadataHelper.getNetCDFMetadata(definitionId, datasetId);
        netCDFMetadataCacheMap.put(uniqueId, fileMetadata);

        return fileMetadata;
    }

    public static File getInputFile(File netCDFDirectory, String definitionId, String datasetId) {
        String uniqueId = NetCDFMetadataBean.getUniqueDatasetId(definitionId, datasetId);
        String filename = Utils.safeFilename(uniqueId);

        return new File(netCDFDirectory, filename);
    }

    public static File getInputFile(File netCDFDirectory, NetCDFMetadataBean inputMetadata) {
        return NcAnimateUtils.getInputFile(netCDFDirectory, inputMetadata.getDefinitionId(), inputMetadata.getDatasetId());
    }

    public static File downloadInputFile(MetadataHelper metadataHelper, S3Client s3Client, File inputFile, NetCDFMetadataBean inputMetadata) throws IOException {
        if (inputFile == null || inputMetadata == null) {
            return null;
        }

        String definitionId = inputMetadata.getDefinitionId();

        if (inputFileCacheMap == null) {
            inputFileCacheMap = new HashMap<String, File>();
        }
        if (inputFileCacheMap.containsKey(definitionId)) {
            File cachedInputFile = inputFileCacheMap.get(definitionId);
            if (cachedInputFile != null && cachedInputFile.canRead()) {
                if (cachedInputFile.getName().equals(inputFile.getName())) {
                    // The file in the cache match the requested one.
                    return cachedInputFile;
                }

                // The cached file is outdated
                LOGGER.warn(String.format("[-] Deleting old input files: %s", cachedInputFile));
                if (!cachedInputFile.delete()) {
                    LOGGER.warn(String.format("Could not delete old input file: %s", cachedInputFile));
                }
            }
        }

        // If the file already exists on disk but it's not in the cache.
        // We can't assume anything about the file. Lets be safe, delete it and redownload it
        // (this should not happen)
        if (inputFile.exists()) {
            LOGGER.warn(String.format("[-] Deleting unexpected files found where the input file needs to be downloaded: %s", inputFile));
            if (!inputFile.delete()) {
                // The file can not be delete, it's pointless to continue
                throw new IOException(String.format("Could not delete input file prior to download: %s", inputFile));
            }
        }

        LOGGER.info(String.format("[+] Downloading NetCDF input file: %s", inputFile));
        metadataHelper.downloadNetCDFFile(inputMetadata, inputFile, s3Client);
        if (!inputFile.canRead()) {
            // The download input file can't be read, it's pointless to continue (this should not happen)
            throw new IOException(String.format("The download input file is not readable: %s", inputFile));
        }

        inputFileCacheMap.put(definitionId, inputFile);

        return inputFile;
    }

    // Create a map of all the layers used in this config
    public static Map<String, NcAnimateLayerBean> getLayers(NcAnimateConfigBean ncAnimateConfig) {
        Map<String, NcAnimateLayerBean> layerMap = new HashMap<String, NcAnimateLayerBean>();
        if (ncAnimateConfig != null) {
            List<NcAnimatePanelBean> panels = ncAnimateConfig.getPanels();
            for (NcAnimatePanelBean panel : panels) {
                List<NcAnimateLayerBean> panelLayers = panel.getLayers();
                if (panelLayers != null) {
                    for (NcAnimateLayerBean panelLayer : panelLayers) {
                        if (panelLayer != null) {
                            layerMap.put(panelLayer.getId().getValue(), panelLayer);
                        }
                    }
                }
            }
        }

        return layerMap;
    }

    private static List<String> getLayersAuthors(NcAnimateConfigBean ncAnimateConfig) {
        List<String> allAuthors = new ArrayList<String>();
        Map<String, NcAnimateLayerBean> layerMap = NcAnimateUtils.getLayers(ncAnimateConfig);
        for (NcAnimateLayerBean layer : layerMap.values()) {
            NcAnimateInputBean inputConf = layer.getInput();
            if (inputConf != null) {
                List<String> authors = inputConf.getAuthors();
                if (authors != null && !authors.isEmpty()) {
                    for (String author : authors) {
                        if (!allAuthors.contains(author)) {
                            allAuthors.add(author);
                        }
                    }
                }
            }
        }
        return allAuthors;
    }

    private static Set<String> getLayersLicences(NcAnimateConfigBean ncAnimateConfig) {
        Set<String> licences = new HashSet<String>();
        Map<String, NcAnimateLayerBean> layerMap = NcAnimateUtils.getLayers(ncAnimateConfig);
        for (NcAnimateLayerBean layer : layerMap.values()) {
            NcAnimateInputBean inputConf = layer.getInput();
            if (inputConf != null) {
                String licence = inputConf.getLicence();
                if (licence != null && !licence.isEmpty()) {
                    licences.add(licence);
                }
            }
        }
        return licences;
    }

    public static File downloadFileToDirectory(URI uri, S3Client s3Client, File directory) throws IOException {
        if (uri == null || directory == null) {
            return null;
        }

        // Extract filename from URI
        String filename = Utils.getFilename(uri);
        File destinationFile = new File(directory, filename);
        if (destinationFile.exists() && destinationFile.canRead()) {
            return destinationFile;
        }

        return NcAnimateUtils.downloadFile(uri, s3Client, destinationFile);
    }

    public static File downloadFile(URI uri, S3Client s3Client, File destinationFile) throws IOException {
        if (uri == null || destinationFile == null) {
            return null;
        }

        Utils.prepareDirectory(destinationFile.getParentFile());

        // Copy URI (S3://, File:// or path) to directory
        boolean sourceFileExists = false;
        if ("s3".equalsIgnoreCase(uri.getScheme())) {
            if (s3Client == null) {
                return null;
            }
            AmazonS3URI s3URI = new AmazonS3URI(uri);
            DownloadManager.download(s3Client, s3URI, destinationFile);
            if (destinationFile.exists()) {
                sourceFileExists = true;
            } else {
                sourceFileExists = s3Client.getS3().doesObjectExist(s3URI.getBucket(), s3URI.getKey());
            }
        } else {
            File sourceFile;
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                sourceFile = new File(uri);
            } else {
                sourceFile = new File(uri.toString());
            }
            if (sourceFile.canRead()) {
                sourceFileExists = true;
                Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        if (!destinationFile.canRead()) {
            if (sourceFileExists) {
                if (destinationFile.exists()) {
                    LOGGER.error(String.format("The file %s exists but is not readable.", destinationFile));
                } else {
                    LOGGER.warn(String.format("The file %s could not be copied to %s.", uri, destinationFile));
                }
            } else {
                LOGGER.warn(String.format("The file %s doesn't exist.", uri));
            }

            return null;
        }

        return destinationFile;
    }

    public static String parseString(NcAnimateTextBean textConf, GeneratorContext context, Map<String, LayerContext> layerContextMap) {
        if (textConf == null) {
            return null;
        }

        List<String> textStr = textConf.getText();
        if (textStr == null || textStr.isEmpty()) {
            return null;
        }

        String parsedStr = NcAnimateUtils.parseString(textStr, context, layerContextMap);
        return parsedStr == null || parsedStr.isEmpty() ? null : parsedStr;
    }

    public static String parseString(String str, GeneratorContext context) {
        List<String> strings = new ArrayList<String>();
        strings.add(str);
        return NcAnimateUtils.parseString(strings, context, null);
    }

    public static String parseString(List<String> strings, GeneratorContext context) {
        return NcAnimateUtils.parseString(strings, context, null);
    }

    /**
     * Replace placeholders with values from config or context, using state parser
     * @param strings String that may contains pattern(s) in the form as "${path options}.
     *     If one or more pattern is not found in the context or config, try with the next string in the list.
     * @return
     */
    public static String parseString(List<String> strings, GeneratorContext context, Map<String, LayerContext> layerContextMap) {
        if (strings == null || strings.isEmpty()) {
            return null;
        }

        for (String str : strings) {
            if (str == null || str.isEmpty()) {
                return str;
            }
            String parsedStr = NcAnimateUtils.parseString(str, context, layerContextMap, false);
            if (parsedStr != null) {
                return parsedStr;
            }
        }


        // If every string contains patterns that are not found in context / config
        String warningMessage = "All texts contains variable(s) that could not be substituted.";
        if (context != null) {
            NcAnimateConfigBean config = context.getNcAnimateConfig();

            if (config != null) {
                warningMessage += String.format("%n%s", config);

                Map<String, NcAnimateLayerBean> layerMap = NcAnimateUtils.getLayers(config);
                if (layerMap != null && !layerMap.isEmpty()) {
                    warningMessage += String.format("%nlayers:%n%s", new JSONObject(layerMap).toString(4));
                }
            }

            warningMessage += String.format("%nctx:%n%s", context);
        }

        if (layerContextMap != null && !layerContextMap.isEmpty()) {
            JSONObject jsonLayerContextMap = new JSONObject();
            for (Map.Entry<String, LayerContext> layerContextEntry : layerContextMap.entrySet()) {
                jsonLayerContextMap.put(
                    layerContextEntry.getKey(),
                    layerContextEntry.getValue().toJSON()
                );
            }
            warningMessage += String.format("%nlayerCtx:%n%s", jsonLayerContextMap.toString(4));
        }
        LOGGER.warn(warningMessage);


        return NcAnimateUtils.parseString(strings.get(strings.size() - 1), context, layerContextMap, true);
    }

    public static String parseString(String str, GeneratorContext context, Map<String, LayerContext> layerContextMap) {
        return NcAnimateUtils.parseString(str, context, layerContextMap, true);
    }

    /**
     *
     * @param str
     * @param context
     * @param layerContextMap
     * @param force True to return the string with whatever can be changed. False to return null if at least one variable can't be substituted.
     * @return
     */
    private static String parseString(String str, GeneratorContext context, Map<String, LayerContext> layerContextMap, boolean force) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        StringBuilder sb = new StringBuilder(),
            patternSb = null;

        int strLen = str.length();
        for (int i=0; i<strLen; i++) {
            char ch = str.charAt(i),
                nextCh = i+1 >= strLen ? '\0' : str.charAt(i+1);

            if (patternSb == null) {
                if (ch == '$' && nextCh == '{') {
                    patternSb = new StringBuilder();
                    i++;
                    continue;
                }
            }

            if (patternSb != null) {
                if (ch == '}') {
                    String path = patternSb.toString();
                    String replacement = NcAnimateUtils.parseStringPattern(path, context, layerContextMap);

                    if (replacement == null) {
                        // If the pattern was not found in context / config
                        LOGGER.warn(String.format("Variable ${%s} not found in: \"%s\".", path, str));
                        if (force) {
                            replacement = String.format("${%s}", path);
                        } else {
                            return null;
                        }
                    }
                    sb.append(replacement);
                    patternSb = null;
                } else {
                    patternSb.append(ch);
                }
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    private static String parseStringPattern(String str, GeneratorContext context, Map<String, LayerContext> layerContextMap) {
        NcAnimateConfigBean config = context.getNcAnimateConfig();
        JSONObject json = config.toJSON();

        str = str.trim();

        String[] strSections = str.split(" ");
        String path = strSections[0].trim();
        String options = strSections.length > 1 ? strSections[1].trim() : null;

        String[] pathParts = path.split("\\.");

        String firstPathPart = pathParts[0].trim();

        Object value = null;

        // Anything else - attempt to find the attribute in the config
        Object rawValue = null;
        JSONObject jsonValue = json;
        int startIndex = 0;

        if ("layers".equals(firstPathPart)) {
            String secondPathPart = pathParts[1].trim();
            if ("authors".equals(secondPathPart)) {
                List<String> authors = NcAnimateUtils.getLayersAuthors(config);
                StringBuilder sb = new StringBuilder();
                if (authors != null && !authors.isEmpty()) {
                    boolean first = true;
                    for (String author : authors) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(", ");
                        }
                        sb.append(author);
                    }
                }
                return sb.toString();

            } else if ("licences".equals(secondPathPart)) {
                Set<String> licences = NcAnimateUtils.getLayersLicences(config);
                StringBuilder sb = new StringBuilder();
                if (licences != null && !licences.isEmpty()) {
                    boolean first = true;
                    for (String licence : licences) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(", ");
                        }
                        sb.append(licence);
                    }
                }
                return sb.toString();
            }
        } else if (firstPathPart.startsWith("layers[")) {
            String[] arrayPropertyParts = NcAnimateUtils.getArrayProperty(firstPathPart);
            if (arrayPropertyParts != null) {
                Map<String, NcAnimateLayerBean> layerMap = NcAnimateUtils.getLayers(config);
                NcAnimateLayerBean layer = layerMap.get(arrayPropertyParts[1]);
                if (layer != null) {
                    jsonValue = layer.toJSON();
                    startIndex = 1;
                }
            }

        } else if ("ctx".equalsIgnoreCase(firstPathPart)) {
            jsonValue = context.toJSON();
            startIndex = 1;

        } else if ("layerCtx".equalsIgnoreCase(firstPathPart)) {
            if (layerContextMap == null || layerContextMap.isEmpty()) {
                return null;
            }

            String layerId = pathParts[1].trim();
            LayerContext layerContext = layerContextMap.get(layerId);
            if (layerContext == null) {
                return null;
            }

            jsonValue = layerContext.toJSON();
            startIndex = 2;
        }


        for (int i=startIndex; i<pathParts.length; i++) {
            String pathPart = pathParts[i].trim();

            rawValue = NcAnimateUtils.getValue(jsonValue, pathPart);
            if (rawValue instanceof JSONObject) {
                jsonValue = (JSONObject) rawValue;
            } else {
                if (i == pathParts.length-1) {
                    // Value found
                    value = rawValue;
                }
                break;
            }
        }

        if (value != null) {
            if (options != null) {
                if (value instanceof DateTime) {
                    Locale locale = NcAnimateUtils.getLocale(context.getNcAnimateConfig());
                    return ((DateTime)value).toString(options.replace('_', ' '), locale);
                }
                if (options.startsWith("%")) {
                    return String.format(options, value);
                }
            }

            return value.toString();
        }

        // Path not found in context / config
        return null;
    }

    private static Object getValue(JSONObject json, String property) {
        if (json == null) {
            return null;
        }

        String[] arrayPropertyParts = NcAnimateUtils.getArrayProperty(property);

        // JSONArray
        // Example:
        //     panels[temp]
        //     will look for json.panels, loop through items looking for one with id = temp
        if (arrayPropertyParts != null) {
            String arrayProperty = arrayPropertyParts[0];
            String arrayIndex = arrayPropertyParts[1];

            JSONArray jsonArray = json.optJSONArray(arrayProperty);
            if (jsonArray != null) {
                for (int i=0; i<jsonArray.length(); i++) {
                    JSONObject jsonItem = jsonArray.optJSONObject(i);
                    if (jsonItem != null) {
                        String itemId = jsonItem.optString("id");
                        if (arrayIndex.equals(itemId)) {
                            return jsonItem;
                        }
                    }
                }

                // Could not find an element with corresponding ID. Try with integer array index.
                try {
                    int intIndex = Integer.parseInt(arrayIndex);
                    if (intIndex < jsonArray.length()) {
                        return jsonArray.get(intIndex);
                    }
                } catch (NumberFormatException ex) {
                    LOGGER.debug(String.format("Array %s do not have any elements with ID %s.",
                            arrayProperty, arrayIndex));
                }
            }

        } else {
            return json.opt(property);
        }

        return null;
    }

    private static String[] getArrayProperty(String propertyStr) {
        Pattern arrayPattern = Pattern.compile("(.+)\\[(.+)\\]");
        Matcher arrayMatcher = arrayPattern.matcher(propertyStr);
        if (arrayMatcher.find()) {
            return new String[] { arrayMatcher.group(1), arrayMatcher.group(2) };
        }

        return null;
    }

    /**
     * Return a map of:
     *     Key: S3 URI (String)
     *     Value: Last modified timestamp
     * NOTE: Never use URL as a key to a hashmap. Java resolve the URL's host to an IP
     *     when determining it's hashcode (and with the equals method), which adds enormous latency.
     *     This issue doesn't seem to apply to URI, but I'm using a String here just to be safe.
     * @param s3Client
     * @param generateFileBean
     * @param ncAnimateConfig
     * @param regionId
     * @return
     * @throws URISyntaxException
     */
    public static Map<String, Long> getOutputFileLastModifiedMap(
            S3Client s3Client,
            NcAnimateGenerateFileBean generateFileBean,
            NcAnimateConfigBean ncAnimateConfig,
            String regionId) throws URISyntaxException {

        Map<String, Long> outputFileLastModifiedMap = new HashMap<String, Long>();

        // Find the oldest last modified of all the rendered file (svg, png, etc)
        // If one file is outdated, they should all be considered outdated.
        if (generateFileBean.getRenderFiles() != null) {
            DateTimeRange outputFileDateTimeRange = generateFileBean.getDateRange();

            for (AbstractNcAnimateRenderFileBean renderFile : generateFileBean.getRenderFiles().values()) {
                String fileURIStr = renderFile.getFileURI();
                if (fileURIStr != null) {
                    // Get regions
                    Map<String, NcAnimateRegionBean> regionMap = ncAnimateConfig.getRegions();
                    if (regionMap != null) {
                        // Get target heights
                        List<Double> targetHeights = ncAnimateConfig.getTargetHeights();
                        if (targetHeights == null || targetHeights.isEmpty()) {
                            targetHeights = new ArrayList<Double>();
                            targetHeights.add(null);
                        }

                        Collection<NcAnimateRegionBean> regions = regionMap.values();
                        // If regionId is specified, filter out regions
                        if (regionId != null) {
                            regions = new ArrayList<NcAnimateRegionBean>();
                            regions.add(regionMap.get(regionId));
                        }

                        for (NcAnimateRegionBean region : regions) {
                            for (Double targetHeight : targetHeights) {
                                GeneratorContext context = new GeneratorContext(ncAnimateConfig);
                                context.setTargetHeight(targetHeight);
                                context.setRegion(region);
                                context.setDateRange(outputFileDateTimeRange);

                                URI fileURI = new URI(NcAnimateUtils.parseString(fileURIStr, context));
                                FileWrapper fileWrapper = new FileWrapper(fileURI, null);
                                Long lastModified = fileWrapper.getS3LastModified(s3Client);
                                outputFileLastModifiedMap.put(fileURI.toString(), lastModified);
                            }
                        }
                    }
                }
            }
        }

        return outputFileLastModifiedMap;
    }

    /**
     * Check if all version of a file (png, svg, mp4, wmv, etc) are more recent than its config files
     * and all of the input files used to generate it.
     * @param s3Client
     * @param generateFileBean
     * @param frameMap
     * @param ncAnimateConfig
     * @param regionId Optional. The region to check. If null, all regions are considered.
     * @param logReason True to log the reason why the product is considered outdated.
     * @return
     * @throws URISyntaxException
     */
    public static boolean isOutdated(
            S3Client s3Client,
            NcAnimateGenerateFileBean generateFileBean,
            Map<DateTimeRange, List<FrameTimetableMap>> frameMap,
            NcAnimateConfigBean ncAnimateConfig,
            String regionId,
            boolean logReason
    ) throws URISyntaxException {

        DateTimeRange outputFileDateTimeRange = generateFileBean.getDateRange();
        List<FrameTimetableMap> productFrameTimetableMapList = frameMap.get(outputFileDateTimeRange);

        if (productFrameTimetableMapList != null) {
            Map<String, Long> productFileLastModifiedMap = getOutputFileLastModifiedMap(s3Client, generateFileBean, ncAnimateConfig, regionId);

            // Find the oldest output file
            boolean missingOutputFile = false;
            Map.Entry<String, Long> oldestProductFileLastModifiedEntry = null;
            for (Map.Entry<String, Long> productFileLastModifiedEntry : productFileLastModifiedMap.entrySet()) {
                Long lastModified = productFileLastModifiedEntry.getValue();
                if (lastModified == null) {
                    missingOutputFile = true;
                    if (logReason) {
                        LOGGER.info(String.format("Missing output file: %s", productFileLastModifiedEntry.getKey()));
                    }
                } else if (oldestProductFileLastModifiedEntry == null || oldestProductFileLastModifiedEntry.getValue() > lastModified) {
                    oldestProductFileLastModifiedEntry = productFileLastModifiedEntry;
                }
            }
            if (missingOutputFile) {
                return true;
            }
            if (oldestProductFileLastModifiedEntry == null) {
                // Nothing to generate? This should not happen
                LOGGER.warn("This product generate no output file");
                return false;
            }

            // Find the newest input file
            // NOTE: If an input file has no lastModified, the product is considered outdated (there is no way to tell)
            //     That should not happen.
            Map<NetCDFMetadataBean, Long> inputLastModifiedMap = new HashMap<NetCDFMetadataBean, Long>();
            for (FrameTimetableMap productFrameTimetableMap : productFrameTimetableMapList) {
                inputLastModifiedMap.putAll(productFrameTimetableMap.getInputLastModifiedMap());
            }
            boolean missingInputLastModified = false;
            Map.Entry<NetCDFMetadataBean, Long> newestInputLastModifiedEntry = null;
            for (Map.Entry<NetCDFMetadataBean, Long> inputLastModifiedEntry : inputLastModifiedMap.entrySet()) {
                Long inputLastModified = inputLastModifiedEntry.getValue();
                if (inputLastModified == null) {
                    // This should not happen
                    missingInputLastModified = true;
                    if (logReason) {
                        LOGGER.info(String.format("Missing input file last modified date: %s", inputLastModifiedEntry.getKey().getId()));
                    }
                } else if (newestInputLastModifiedEntry == null || newestInputLastModifiedEntry.getValue() < inputLastModified) {
                    newestInputLastModifiedEntry = inputLastModifiedEntry;
                }
            }
            if (missingInputLastModified) {
                return true;
            }

            // Check if the newest input file is more recent than the oldest output file
            // NOTE: NcAnimate could produce output file without the need for input file (unlikely)
            if (newestInputLastModifiedEntry != null) {
                if (newestInputLastModifiedEntry.getValue() > oldestProductFileLastModifiedEntry.getValue()) {
                    if (logReason) {
                        LOGGER.info(String.format("Input file (%s - %s) is more recent than output file (%s - %s)",
                                newestInputLastModifiedEntry.getKey().getId(),
                                new DateTime(newestInputLastModifiedEntry.getValue()),
                                oldestProductFileLastModifiedEntry.getKey(),
                                new DateTime(oldestProductFileLastModifiedEntry.getValue())));
                    }

                    return true;
                }
            }

            // Find the newest config file
            AbstractNcAnimateBean lastModifiedConfigPart = ncAnimateConfig.getLastModifiedConfigPart();
            long configLastModified = lastModifiedConfigPart.getLastModified();

            if (configLastModified > oldestProductFileLastModifiedEntry.getValue()) {
                if (logReason) {
                    LOGGER.info(String.format("Configuration file (%s - %s) is more recent than output file (%s - %s)",
                            lastModifiedConfigPart.getId(),
                            new DateTime(configLastModified),
                            oldestProductFileLastModifiedEntry.getKey(),
                            new DateTime(oldestProductFileLastModifiedEntry.getValue())));
                }

                return true;
            }
        }

        return false;
    }

    public static DateTimeZone getTimezone(NcAnimateConfigBean ncAnimateConfig) {
        DateTimeZone defaultTimezone = NcAnimateRenderBean.DEFAULT_DATETIMEZONE;

        if (ncAnimateConfig == null) {
            return defaultTimezone;
        }

        NcAnimateRenderBean render = ncAnimateConfig.getRender();
        if (render == null) {
            return defaultTimezone;
        }

        return render.getDateTimeZone();
    }

    public static Locale getLocale(NcAnimateConfigBean ncAnimateConfig) {
        Locale defaultLocale = NcAnimateRenderBean.DEFAULT_LOCALE;

        if (ncAnimateConfig == null) {
            return defaultLocale;
        }

        NcAnimateRenderBean render = ncAnimateConfig.getRender();
        if (render == null) {
            return defaultLocale;
        }

        return render.getLocaleObject();
    }

    // Used for debugging (find memory leaks)
    public static void printMemoryUsage(String step) {
        float mb = 1024 * 1024;
        Runtime instance = Runtime.getRuntime();

        instance.gc();

        long totalMemory = instance.totalMemory();
        long freeMemory = instance.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        LOGGER.info(String.format("#### %s%n    Free Memory: %.2f MB / %.2f MB (used: %.2f MB)",
                step,
                freeMemory / mb,
                totalMemory / mb,
                usedMemory / mb));
    }
}
