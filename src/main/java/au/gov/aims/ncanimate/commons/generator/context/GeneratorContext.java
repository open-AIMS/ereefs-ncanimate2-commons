/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.generator.context;

import au.gov.aims.aws.s3.FileWrapper;
import au.gov.aims.ereefs.Utils;
import au.gov.aims.ereefs.bean.metadata.TimeIncrement;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateBboxBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateCanvasBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateConfigBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateIdBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateLayerBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateNetCDFTrueColourVariableBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateNetCDFVariableBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimatePaddingBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimatePanelBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateRegionBean;
import au.gov.aims.ereefs.bean.ncanimate.render.AbstractNcAnimateRenderFileBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderMapBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderVideoBean;
import au.gov.aims.json.JSONWrapperObject;
import au.gov.aims.ncanimate.commons.NcAnimateUtils;
import au.gov.aims.ncanimate.commons.timetable.DateTimeRange;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class GeneratorContext {
    private static final Logger LOGGER = Logger.getLogger(GeneratorContext.class);

    private static final String PRODUCT_DIR_NAME = "output/product";
    private static final String FRAME_DIR_NAME = "output/frame";

    private static final String NETCDF_DIR_NAME = "input/netcdf";
    private static final String LAYER_DIR_NAME = "input/layer";
    private static final String PALETTE_DIR_NAME = "input/palette";
    private static final String STYLE_DIR_NAME = "input/style"; // Layer SLD style sheets

    public static final String FRAME_FILENAME_PREFIX = "frame";
    public static final String VIDEO_FRAME_DIRECTORY = "videoFrames";
    public static final NcAnimateRenderMapBean.MapFormat VIDEO_FRAME_FORMAT = NcAnimateRenderMapBean.MapFormat.PNG;

    private NcAnimateConfigBean ncAnimateConfig;
    private NcAnimatePanelBean panelConfig;

    private File workingDirectory;

    private File frameDirectory;
    private URI s3FrameDirectory;
    private File outputDirectory;

    private String outputFilenamePrefix; // "map" or "video"

    private DateTimeRange dateRange;

    private AbstractNcAnimateRenderFileBean renderFile;
    private NcAnimateRegionBean region;
    private Double targetHeight;

    private TimeIncrement frameTimeIncrement;

    // Input files
    private File netCDFDirectory;
    private File layerDirectory;
    private File paletteDirectory;
    private File styleDirectory;

    // Size of the rendered images
    private Integer canvasWidth;
    private Integer canvasHeight;

    // Used when the product is resized in config and/or when padding is added
    private Integer productWidth;
    private Integer productHeight;
    private NcAnimatePaddingBean padding;
    private DateTime generationDate;

    public GeneratorContext(NcAnimateConfigBean ncAnimateConfig) {
        this.ncAnimateConfig = ncAnimateConfig;
        this.generationDate = new DateTime();
    }

    public NcAnimatePanelBean getPanelConfig() {
        return panelConfig;
    }

    public void setPanelConfig(NcAnimatePanelBean panelConfig) {
        this.panelConfig = panelConfig;
    }

    public NcAnimateConfigBean getNcAnimateConfig() {
        return this.ncAnimateConfig;
    }

    public File getWorkingDirectory() {
        if (this.workingDirectory == null) {
            this.workingDirectory = this.generateWorkingDirectory();
        }
        return this.workingDirectory;
    }

    public File getFrameDirectory() {
        if (this.frameDirectory == null) {
            this.frameDirectory = this.generateFrameDirectory();
        }
        return this.frameDirectory;
    }

    public URI getS3FrameDirectory() {
        if (this.s3FrameDirectory == null) {
            this.s3FrameDirectory = this.generateS3FrameDirectory();
        }
        return this.s3FrameDirectory;
    }

    public File getOutputDirectory() {
        if (this.outputDirectory == null) {
            this.outputDirectory = this.generateOutputDirectory();
        }
        return this.outputDirectory;
    }

    public String getOutputFilenamePrefix() {
        return this.outputFilenamePrefix;
    }

    public void setOutputFilenamePrefix(String outputFilenamePrefix) {
        this.outputFilenamePrefix = outputFilenamePrefix;
    }

    public DateTimeRange getDateRange() {
        return this.dateRange;
    }

    public void setDateRange(DateTimeRange dateRange) {
        this.dateRange = dateRange;
    }

    public AbstractNcAnimateRenderFileBean getRenderFile() {
        return this.renderFile;
    }

    public void setRenderFile(AbstractNcAnimateRenderFileBean renderFile) {
        this.renderFile = renderFile;
        this.resetCalculatedValues();
    }

    public NcAnimateRegionBean getRegion() {
        return this.region;
    }

    public void setRegion(NcAnimateRegionBean region) {
        this.region = region;
        this.resetCalculatedValues();
    }

    private void resetCalculatedValues() {
        // Reset all the attributes that were calculated using the renderFile
        // calculateCanvasDimensions
        this.canvasWidth = null;
        this.canvasHeight = null;

        // calculateProductDimensions
        this.padding = null;
        this.productWidth = null;
        this.productHeight = null;
    }

    public Double getTargetHeight() {
        return this.targetHeight;
    }

    public void setTargetHeight(Double targetHeight) {
        this.targetHeight = targetHeight;
    }

    public TimeIncrement getFrameTimeIncrement() {
        return this.frameTimeIncrement;
    }

    public void setFrameTimeIncrement(TimeIncrement frameTimeIncrement) {
        this.frameTimeIncrement = frameTimeIncrement;
    }

    public File getNetCDFDirectory() {
        if (this.netCDFDirectory == null) {
            this.netCDFDirectory = new File(this.getWorkingDirectory(), NETCDF_DIR_NAME);
            if (this.netCDFDirectory != null && !Utils.prepareDirectory(this.netCDFDirectory)) {
                LOGGER.error(String.format("The NetCDF input directory %s could not be created.", this.netCDFDirectory));
            }
        }
        return this.netCDFDirectory;
    }

    public File getLayerDirectory() {
        if (this.layerDirectory == null) {
            this.layerDirectory = new File(this.getWorkingDirectory(), LAYER_DIR_NAME);
            if (this.layerDirectory != null && !Utils.prepareDirectory(this.layerDirectory)) {
                LOGGER.error(String.format("The layer input directory %s could not be created.", this.layerDirectory));
            }
        }
        return this.layerDirectory;
    }

    public File getPaletteDirectory() {
        if (this.paletteDirectory == null) {
            this.paletteDirectory = new File(this.getWorkingDirectory(), PALETTE_DIR_NAME);
            if (this.paletteDirectory != null && !Utils.prepareDirectory(this.paletteDirectory)) {
                LOGGER.error(String.format("The palette input directory %s could not be created.", this.paletteDirectory));
            }
        }
        return this.paletteDirectory;
    }

    public File getStyleDirectory() {
        if (this.styleDirectory == null) {
            this.styleDirectory = new File(this.getWorkingDirectory(), STYLE_DIR_NAME);
            if (this.styleDirectory != null && !Utils.prepareDirectory(this.styleDirectory)) {
                LOGGER.error(String.format("The style input directory %s could not be created.", this.styleDirectory));
            }
        }
        return this.styleDirectory;
    }


    public int getPanelWidth(NcAnimatePanelBean panelConf) {
        return NcAnimateUtils.getInt(panelConf.getWidth());
    }

    public int getScaledPanelWidth(NcAnimatePanelBean panelConf) {
        return NcAnimateUtils.scale(this.getPanelWidth(panelConf), this.getRenderScale());
    }

    public float getRenderScale() {
        NcAnimateConfigBean ncAnimateConfig = this.getNcAnimateConfig();
        NcAnimateRenderBean render = ncAnimateConfig == null ? null : ncAnimateConfig.getRender();
        return render == null ? 1 : render.getScale();
    }

    public Integer getPanelHeight(NcAnimatePanelBean panelConf) {
        if (this.region == null) {
            LOGGER.warn(String.format("Region is null"));
            return null;
        }
        NcAnimateBboxBean bbox = this.region.getBbox();

        Double east = bbox.getEast(),
            west = bbox.getWest(),
            north = bbox.getNorth(),
            south = bbox.getSouth();

        if (east == null || west == null || north == null || south == null) {
            LOGGER.warn(String.format("Invalid region bbox for %s: %s", this.region.getId(), bbox));
            return null;
        }

        double bboxWidth = Math.abs(east - west);
        double bboxHeight = Math.abs(north - south);

        double bboxRatio = bboxHeight / bboxWidth;

        return (int)Math.round(this.getPanelWidth(panelConf) * bboxRatio);
    }

    public Integer getScaledPanelHeight(NcAnimatePanelBean panelConf) {
        return NcAnimateUtils.scale(this.getPanelHeight(panelConf), this.getRenderScale());
    }


    public Integer getCanvasWidth() {
        if (this.canvasWidth == null) {
            this.calculateCanvasDimensions();
        }
        return this.canvasWidth;
    }

    public Integer getScaledCanvasWidth() {
        return NcAnimateUtils.scale(this.getCanvasWidth(), this.getRenderScale());
    }

    public Integer getCanvasHeight() {
        if (this.canvasHeight == null) {
            this.calculateCanvasDimensions();
        }
        return this.canvasHeight;
    }

    public int getScaledCanvasHeight() {
        return NcAnimateUtils.scale(this.getCanvasHeight(), this.getRenderScale());
    }

    public NcAnimatePaddingBean getPadding() {
        if (this.padding == null) {
            this.calculateProductDimensions();
        }
        return this.padding;
    }

    public DateTime getGenerationDate() {
        return this.generationDate;
    }

    public Integer getMaxWidth() {
        if (this.renderFile == null) {
            return null;
        }

        Integer maxWidth = this.renderFile.getMaxWidth();
        Integer maxHeight = this.renderFile.getMaxHeight();

        if (maxWidth == null) {
            if (maxHeight == null) {
                return this.getCanvasWidth();
            }
            return (int)Math.round(1.0 * this.getCanvasWidth() / this.getCanvasHeight() * maxHeight);
        }
        return maxWidth;
    }

    public Integer getScaledMaxWidth() {
        Integer maxWidth = NcAnimateUtils.scale(this.getMaxWidth(), this.getRenderScale());
        return maxWidth == null ? this.getScaledCanvasWidth() : maxWidth;
    }

    public Integer getMaxHeight() {
        if (this.renderFile == null) {
            return null;
        }

        Integer maxWidth = this.renderFile.getMaxWidth();
        Integer maxHeight = this.renderFile.getMaxHeight();

        if (maxHeight == null) {
            if (maxWidth == null) {
                return this.getCanvasHeight();
            }
            return (int)Math.round(1.0 * this.getCanvasHeight() / this.getCanvasWidth() * maxWidth);
        }
        return maxHeight;
    }

    public Integer getScaledMaxHeight() {
        Integer maxHeight = NcAnimateUtils.scale(this.getMaxHeight(), this.getRenderScale());
        return maxHeight == null ? this.getScaledCanvasHeight() : maxHeight;
    }

    public Integer getProductWidth() {
        if (this.productWidth == null) {
            this.calculateProductDimensions();
        }
        return this.productWidth;
    }

    public Integer getProductHeight() {
        if (this.productHeight == null) {
            this.calculateProductDimensions();
        }
        return this.productHeight;
    }

    public static String getFilenameDate(TimeIncrement fileTimeIncrement, DateTime fileStartDate) {
        if (fileStartDate == null) {
            return "";
        }

        if (fileTimeIncrement == null) {
            return fileStartDate.toString();
        }

        switch (fileTimeIncrement.getSafeUnit()) {
            case MINUTE:
            case HOUR:
                return String.format("%04d-%02d-%02d_%02dh%02d",
                        fileStartDate.getYear(),
                        fileStartDate.getMonthOfYear(),
                        fileStartDate.getDayOfMonth(),
                        fileStartDate.getHourOfDay(),
                        fileStartDate.getMinuteOfHour());

            case ETERNITY:
            case DAY:
                return String.format("%04d-%02d-%02d",
                        fileStartDate.getYear(),
                        fileStartDate.getMonthOfYear(),
                        fileStartDate.getDayOfMonth());

            case MONTH:
                return String.format("%04d-%02d",
                        fileStartDate.getYear(),
                        fileStartDate.getMonthOfYear());

            case YEAR:
                return String.format("%04d",
                        fileStartDate.getYear());

            default:
                throw new IllegalArgumentException(String.format("Unsupported file time increment: %s", fileTimeIncrement));
        }
    }

    public String getOutputFilename() {
        StringBuilder filenameSb = new StringBuilder(this.outputFilenamePrefix == null ? "UNNAMED" : this.outputFilenamePrefix);

        TimeIncrement timeIncrement = null;
        NcAnimateRenderBean render = this.ncAnimateConfig.getRender();
        if (render != null) {
            timeIncrement = (this.renderFile != null && (this.renderFile instanceof NcAnimateRenderVideoBean)) ?
                    render.getVideoTimeIncrement() :
                    this.frameTimeIncrement;
        }

        if (this.dateRange != null) {
            DateTime startDate = this.dateRange.getStartDate();
            if (startDate != null) {
                filenameSb.append("_").append(GeneratorContext.getFilenameDate(timeIncrement, startDate));
            }
        }

        if (this.region != null) {
            filenameSb.append("_").append(this.region.getId().getValue());
        }

        if (this.targetHeight != null) {
            filenameSb.append(String.format("_%.1f", this.targetHeight));
        }

        if (this.renderFile != null && this.renderFile.getFileExtension() != null) {
            filenameSb.append(".").append(this.renderFile.getFileExtension());
        }

        return Utils.safeFilename(filenameSb.toString());
    }

    public File getOutputFile() {
        return new File(this.getOutputDirectory(), this.getOutputFilename());
    }

    private File generateWorkingDirectory() {
        NcAnimateRenderBean render = this.getNcAnimateConfig().getRender();
        File workingDirectory = render.getWorkingDirectoryFile();
        if (workingDirectory != null && !Utils.prepareDirectory(workingDirectory)) {
            LOGGER.error(String.format("The working directory %s could not be created.", workingDirectory));
        }
        return workingDirectory;
    }

    private File generateFrameDirectory() {
        File workingDirectory = this.getWorkingDirectory();
        File frameDirectory = new File(workingDirectory, FRAME_DIR_NAME);

        String frameDirPath = this.generateFrameDirectoryPath();
        File productFrameDirectory = new File(frameDirectory, Utils.safeFilename(this.ncAnimateConfig.getId().getValue()) + "/" +  frameDirPath);

        if (!Utils.prepareDirectory(productFrameDirectory)) {
            LOGGER.error(String.format("The frame output directory %s could not be created.", productFrameDirectory));
        }

        return productFrameDirectory;
    }

    public String generateFrameDirectoryPath() {
        StringBuilder frameDirPathSb = new StringBuilder();

        if (this.getRegion() != null) {
            frameDirPathSb.append("/").append(Utils.safeFilename(this.getRegion().getId().getValue()));
        }

        if (this.getTargetHeight() != null) {
            frameDirPathSb.append("/").append(Utils.safeFilename(String.format("height_%.1f", this.getTargetHeight())));
        }

        String frameDirPathStr = frameDirPathSb.toString();

        if (!frameDirPathStr.endsWith("/")) {
            frameDirPathStr += "/";
        }
        while (frameDirPathStr.startsWith("/")) {
            frameDirPathStr = frameDirPathStr.substring(1);
        }

        return frameDirPathStr;
    }

    private URI generateS3FrameDirectory() {
        URI uri = null;

        NcAnimateConfigBean ncAnimateConfig = this.getNcAnimateConfig();
        NcAnimateRenderBean renderConf = ncAnimateConfig.getRender();
        if (renderConf != null) {
            String frameDirectoryUriStr = NcAnimateUtils.parseString(renderConf.getFrameDirectoryUri(), this);

            if (frameDirectoryUriStr != null) {
                String path = this.generateFrameDirectoryPath();
                if (!frameDirectoryUriStr.endsWith("/")) {
                    frameDirectoryUriStr += "/";
                }

                try {
                    uri = new URI(frameDirectoryUriStr + path);
                } catch (URISyntaxException ex) {
                    // This should not happen...
                    LOGGER.error(String.format("Error occurred while creating the URI: %s%s", frameDirectoryUriStr, path), ex);
                }
            }
        }

        return uri;
    }

    private File generateOutputDirectory() {
        File workingDirectory = this.getWorkingDirectory();
        File outputDirectory = new File(workingDirectory, PRODUCT_DIR_NAME);
        File productOutputDirectory = new File(outputDirectory, Utils.safeFilename(this.getNcAnimateConfig().getId().getValue()));
        if (!Utils.prepareDirectory(productOutputDirectory)) {
            LOGGER.error(String.format("The output directory %s could not be created.", productOutputDirectory));
        }
        return productOutputDirectory;
    }

    /**
     * Calculate the canvas dimensions for specified NcAnimate configuration.
     */
    private void calculateCanvasDimensions() {
        NcAnimateConfigBean ncAnimateConfig = this.getNcAnimateConfig();

        NcAnimateCanvasBean canvasConf = ncAnimateConfig.getCanvas();
        this.canvasWidth = 0;
        this.canvasHeight = 0;

        NcAnimatePaddingBean paddingConf = canvasConf.getPadding();
        if (paddingConf != null) {
            this.canvasWidth = NcAnimateUtils.getInt(paddingConf.getLeft()) + NcAnimateUtils.getInt(paddingConf.getRight());
            this.canvasHeight = NcAnimateUtils.getInt(paddingConf.getTop()) + NcAnimateUtils.getInt(paddingConf.getBottom());
        }
        int betweenPanelPadding = NcAnimateUtils.getInt(canvasConf.getPaddingBetweenPanels());

        List<NcAnimatePanelBean> panelConfs = ncAnimateConfig.getPanels();

        int maxPanelHeight = 0;
        if (panelConfs != null) {
            for (NcAnimatePanelBean panelConf : panelConfs) {
                int leftMargin = 0, rightMargin = 0, topMargin = 0, bottomMargin = 0;
                NcAnimatePaddingBean margin = panelConf.getMargin();
                if (margin != null) {
                    leftMargin = NcAnimateUtils.getInt(margin.getLeft());
                    rightMargin = NcAnimateUtils.getInt(margin.getRight());

                    topMargin = NcAnimateUtils.getInt(margin.getTop());
                    bottomMargin = NcAnimateUtils.getInt(margin.getBottom());
                }

                int panelWidth = this.getPanelWidth(panelConf);
                Integer panelHeight = this.getPanelHeight(panelConf);

                // Add to canvas size.
                if (panelHeight != null) {
                    panelHeight += topMargin + bottomMargin;
                    if (panelHeight > maxPanelHeight) {
                        maxPanelHeight = panelHeight;
                    }
                }
                this.canvasWidth += panelWidth + betweenPanelPadding + leftMargin + rightMargin;
            }
        }
        // Remove the last "between" padding for the canvas width.
        this.canvasWidth -= betweenPanelPadding;
        this.canvasHeight += maxPanelHeight;
    }

    private void calculateProductDimensions() {
        // Adjust canvasWidth & canvasHeight to fit width and height limitation in encoders (such as H.264 used with MP4)
        // NOTE: Division between 2 int is an integer division, which automatically crop the decimal part.
        int addedWidth = 0,
            addedHeight = 0;

        Integer[] blockSize = null;
        if (this.renderFile != null && (this.renderFile instanceof NcAnimateRenderVideoBean)) {
            NcAnimateRenderVideoBean renderVideoFile = (NcAnimateRenderVideoBean) this.renderFile;
            blockSize = renderVideoFile.getBlockSize();
        }
        if (blockSize == null || blockSize.length != 2) {
            blockSize = new Integer[]{ 1, 1 };
        }

        int maxWidth = this.getScaledMaxWidth();
        int maxHeight = this.getScaledMaxHeight();

        JSONObject jsonPadding = new JSONObject()
                .put("top", 0)
                .put("bottom", 0)
                .put("left", 0)
                .put("right", 0);

        this.productWidth = GeneratorContext.getCeilQuantisedValue(maxWidth, blockSize[0]);
        addedWidth = this.productWidth - maxWidth;
        if (addedWidth > 0) {
            LOGGER.debug("Image width was increased by " + (this.productWidth - maxWidth) + "px to comply with H.264 restrictions.");

            int left = addedWidth / 2;
            int right = addedWidth - left;

            jsonPadding.put("left", left)
                    .put("right", right);
        }

        this.productHeight = GeneratorContext.getCeilQuantisedValue(maxHeight, blockSize[1]);
        addedHeight = this.productHeight - maxHeight;
        if (addedHeight > 0) {
            LOGGER.debug("Image height was increased by " + (this.productHeight - maxHeight) + "px to comply with H.264 restrictions.");

            int top = addedHeight / 2;
            int bottom = addedHeight - top;

            jsonPadding.put("top", top)
                    .put("bottom", bottom);
        }

        try {
            this.padding = new NcAnimatePaddingBean(new JSONWrapperObject(jsonPadding));
        } catch(Exception ex) {
            // This should not happen
            LOGGER.error("Error occurred while creating the GeneratorContext padding object.");
        }
    }

    private static int getCeilQuantisedValue(int value, Integer modulus) {
        if (modulus == null || modulus <= 1 || value % modulus == 0) {
            return value;
        }

        return (value / modulus + 1) * modulus;
    }

    public File getFrameFileWithoutExtension(DateTimeRange frameDateRange) {
        return new File(this.getFrameDirectory(), this.getFrameFilenameWithoutExtension(frameDateRange));
    }

    public String getFrameFilenameWithoutExtension(DateTimeRange frameDateRange) {
        StringBuilder filenameSb = new StringBuilder(FRAME_FILENAME_PREFIX);

        if (frameDateRange != null) {
            filenameSb.append("_").append(GeneratorContext.getFilenameDate(this.getFrameTimeIncrement(), frameDateRange.getStartDate()));
        }

        return Utils.safeFilename(filenameSb.toString());
    }


    /**
     * Get the frame file, in all expected rendered format
     * NOTE: A frame file might need to be generated into multiple format (svg, png, jpg, etc)
     * @return
     */
    public Map<NcAnimateRenderMapBean.MapFormat, FileWrapper> getFrameFileWrapperMap(DateTimeRange frameDateRange) {
        Map<NcAnimateRenderMapBean.MapFormat, FileWrapper> frameFileMap =
                new EnumMap<NcAnimateRenderMapBean.MapFormat, FileWrapper>(NcAnimateRenderMapBean.MapFormat.class);

        NcAnimateConfigBean ncAnimateConfig = this.getNcAnimateConfig();
        NcAnimateRenderBean renderConf = ncAnimateConfig.getRender();

        if (renderConf != null) {
            Map<String, NcAnimateRenderMapBean> mapConfs = renderConf.getMaps();
            if (mapConfs != null) {
                for (NcAnimateRenderMapBean renderMapConf : mapConfs.values()) {
                    if (renderMapConf != null) {
                        this.addFrameFile(frameFileMap, frameDateRange, renderMapConf.getFormat());
                    }
                }
            }

            Map<String, NcAnimateRenderVideoBean> videoConfs = renderConf.getVideos();
            if (videoConfs != null) {
                for (NcAnimateRenderVideoBean renderVideoConf : videoConfs.values()) {
                    if (renderVideoConf != null) {
                        NcAnimateRenderVideoBean.VideoFormat videoFormat = renderVideoConf.getFormat();
                        switch(videoFormat) {
                            case MP4:
                            case WMV:
                                this.addFrameFile(frameFileMap, frameDateRange, VIDEO_FRAME_FORMAT);
                                break;
                        }
                    }
                }
            }
        }

        return frameFileMap;
    }

    private void addFrameFile(Map<NcAnimateRenderMapBean.MapFormat, FileWrapper> frameFileMap, DateTimeRange frameDateRange, NcAnimateRenderMapBean.MapFormat format) {
        if (!frameFileMap.containsKey(format)) {
            frameFileMap.put(format, this.getFrameFileWrapper(frameDateRange, format));
        }
    }

    private FileWrapper getFrameFileWrapper(DateTimeRange frameDateRange, NcAnimateRenderMapBean.MapFormat format) {
        String frameFilenameWithoutExtension = this.getFrameFilenameWithoutExtension(frameDateRange);
        String filename = frameFilenameWithoutExtension + "." + format.getExtension();

        URI s3FrameDirectory = this.getS3FrameDirectory();
        URI s3FileUri = null;
        if (s3FrameDirectory != null) {
            try {
                s3FileUri = new URI(s3FrameDirectory.toString() + filename);
            } catch (URISyntaxException ex) {
                // This should not happen...
                LOGGER.error(String.format("Error occurred while creating the URI: %s%s", s3FrameDirectory, filename), ex);
            }
        }

        File frameDirectory = this.getFrameDirectory();
        if (!Utils.prepareDirectory(frameDirectory)) {
            LOGGER.error(String.format("The frame output directory %s could not be created.", frameDirectory));
        }

        return new FileWrapper(
            s3FileUri,
            new File(frameDirectory, filename)
        );
    }

    public Map<String, NcAnimateNetCDFVariableBean> getPanelVariableMap() {
        return getPanelVariableMap(this.panelConfig);
    }

    // Map:
    //   Key: Panel ID (String)
    //   Value: Map:
    //     Key: Variable ID (String)
    //     Value: NcAnimateNetCDFVariableBean
    private Map<String, Map<String, NcAnimateNetCDFVariableBean>> getVariableMap() {
        Map<String, Map<String, NcAnimateNetCDFVariableBean>> variableMap =
                new HashMap<String, Map<String, NcAnimateNetCDFVariableBean>>();

        for (NcAnimatePanelBean panelConf : this.ncAnimateConfig.getPanels()) {
            NcAnimateIdBean panelId = panelConf.getId();
            String panelIdStr = panelId == null ? null : panelId.getValue();
            if (panelIdStr != null && !panelIdStr.isEmpty()) {
                Map<String, NcAnimateNetCDFVariableBean> panelVariableMap = getPanelVariableMap(panelConf);
                if (panelVariableMap != null && !panelVariableMap.isEmpty()) {
                    variableMap.put(panelIdStr, panelVariableMap);
                }
            }
        }
        return variableMap;
    }

    private static SortedMap<String, NcAnimateNetCDFVariableBean> getPanelVariableMap(NcAnimatePanelBean panelConfig) {
        SortedMap<String, NcAnimateNetCDFVariableBean> usedVariables =
                new TreeMap<String, NcAnimateNetCDFVariableBean>();

        if (panelConfig != null) {
            List<NcAnimateLayerBean> layers = panelConfig.getLayers();
            if (layers != null) {
                for (NcAnimateLayerBean layer : layers) {
                    NcAnimateNetCDFVariableBean variable = layer.getVariable();
                    if (variable != null) {
                        usedVariables.put(variable.getVariableId(), variable);
                    }

                    NcAnimateNetCDFVariableBean arrowVariable = layer.getArrowVariable();
                    if (arrowVariable != null) {
                        usedVariables.put(arrowVariable.getVariableId(), arrowVariable);
                    }

                    Map<String, NcAnimateNetCDFTrueColourVariableBean> trueColourVariables = layer.getTrueColourVariables();
                    if (trueColourVariables != null) {
                        usedVariables.putAll(trueColourVariables);
                    }
                }
            }
        }

        return usedVariables;
    }

    public String getPanelVariableIdsStr() {
        return this.getPanelVariableIdsStr(this.getPanelVariableMap());
    }

    private String getPanelVariableIdsStr(Map<String, NcAnimateNetCDFVariableBean> panelVariableMap) {
        if (panelVariableMap == null || panelVariableMap.isEmpty()) {
            return null;
        }

        return String.join(", ", panelVariableMap.keySet());
    }

    public String getVariableIdsStr() {
        return this.getVariableIdsStr(this.getVariableMap());
    }

    private String getVariableIdsStr(Map<String, Map<String, NcAnimateNetCDFVariableBean>> variableMap) {
        if (variableMap == null || variableMap.isEmpty()) {
            return null;
        }

        SortedMap<String, NcAnimateNetCDFVariableBean> usedVariables =
                new TreeMap<String, NcAnimateNetCDFVariableBean>();

        for (Map<String, NcAnimateNetCDFVariableBean> panelVariableMap : variableMap.values()) {
            usedVariables.putAll(panelVariableMap);
        }

        if (usedVariables.isEmpty()) {
            return null;
        }

        return String.join(", ", usedVariables.keySet());
    }

    public JSONObject toJSON() {
        JSONObject jsonPadding = null;
        NcAnimatePaddingBean padding = this.getPadding();
        if (padding != null) {
            jsonPadding = padding.toJSON();
            if (jsonPadding.isEmpty()) {
                jsonPadding = null;
            }
        }

        JSONObject jsonPanel = null;
        if (this.panelConfig != null) {
            jsonPanel = new JSONObject();

            Map<String, NcAnimateNetCDFVariableBean> panelVariableMap = this.getPanelVariableMap();
            if (panelVariableMap != null && !panelVariableMap.isEmpty()) {
                JSONObject jsonVariableMap = new JSONObject();
                for (Map.Entry<String, NcAnimateNetCDFVariableBean> panelVariableEntry : panelVariableMap.entrySet()) {
                    jsonVariableMap.put(panelVariableEntry.getKey(), panelVariableEntry.getValue().toJSON());
                }
                jsonPanel.put("config", this.panelConfig.toJSON());
                jsonPanel.put("variables", jsonVariableMap);
                jsonPanel.put("variableIds", this.getPanelVariableIdsStr(panelVariableMap));
            }

            jsonPanel.put("config", this.panelConfig.toJSON());
        }

        // Map all variables from all panels
        String variableIdsStr = this.getVariableIdsStr();

        return new JSONObject()
            .put("ncAnimateConfig", this.ncAnimateConfig == null ? null : this.ncAnimateConfig.getId().getValue())
            .put("panel", jsonPanel)
            .put("variableIds", variableIdsStr)
            .put("workingDirectory", this.getWorkingDirectory())

            .put("renderFile", this.renderFile == null ? null : this.renderFile.toJSON())
            .put("frameDirectory", this.getFrameDirectory())
            .put("frameFilenamePrefix", FRAME_FILENAME_PREFIX)
            .put("videoFrameDirectory", new File(this.getFrameDirectory(), VIDEO_FRAME_DIRECTORY))

            .put("outputDirectory", this.getOutputDirectory())
            .put("outputFilenamePrefix", this.outputFilenamePrefix) // "video" or "map"
            .put("outputFilename", this.getOutputFilename()) // "video_gbr4_v2_temp-wind-salt-current_2019-01-14.mp4"
            .put("outputFile", this.getOutputFile()) // "/output/directory/video_gbr4_v2_temp-wind-salt-current_2019-01-14.mp4"

            .put("dateRange", this.dateRange == null ? null : this.dateRange.toJSON())

            // Used with templates
            .put("dateFrom", this.dateRange == null ? null : this.dateRange.getStartDate())
            .put("dateTo", this.dateRange == null ? null : this.dateRange.getEndDate())


            .put("region", this.region == null ? null : this.region.toJSON())
            .put("targetHeight", this.targetHeight == null ? "0" : this.targetHeight)

            .put("frameTimeIncrement", this.frameTimeIncrement == null ? null : this.frameTimeIncrement.toJSON())
            .put("framePeriod", NcAnimateUtils.getTimeIncrementLabel(this.frameTimeIncrement))

            .put("netCDFDirectory", this.getNetCDFDirectory())
            .put("layerDirectory", this.getLayerDirectory())
            .put("paletteDirectory", this.getPaletteDirectory())
            .put("styleDirectory", this.getStyleDirectory())

            // Size of the rendered image (frame).
            .put("canvasWidth", this.getScaledCanvasWidth())
            .put("canvasHeight", this.getScaledCanvasHeight())

            .put("generationDate", this.generationDate)

            // NOTE: NcAnimate does some calculations, but doesn't create resized images.
            //     The only image generated by NcAnimate is of canvasWidth x canvasHeight dimensions.
            //     The image can be manipulated after generation using post-processing treatment software
            //     such as ffmpeg or image magick. The following numbers are just there to help figuring
            //     out which number should be used.

            // Expected resized image, as specified by the config
            // NOTE: the config only specify one of the 2 (for example, maxWidth) and this class calculate the other.
            .put("maxWidth", this.getScaledMaxWidth())
            .put("maxHeight", this.getScaledMaxHeight())
            // Padding needed to be added to the resized image in order to get the the final size (used when blockSize is specified)
            .put("padding", jsonPadding)
            // Final dimensions of the image, after resize and padding added. The final dimensions respect the blockSize, if specified.
            .put("productWidth", this.getProductWidth())
            .put("productHeight", this.getProductHeight());
    }

    @Override
    public String toString() {
        return this.toJSON().toString(4);
    }
}
