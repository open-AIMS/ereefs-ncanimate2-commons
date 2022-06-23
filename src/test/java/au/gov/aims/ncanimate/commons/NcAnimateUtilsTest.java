/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons;

import au.gov.aims.ereefs.bean.ncanimate.NcAnimateConfigBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateRegionBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderMapBean;
import au.gov.aims.ereefs.bean.ncanimate.render.NcAnimateRenderVideoBean;
import au.gov.aims.ereefs.database.CacheStrategy;
import au.gov.aims.ereefs.helper.NcAnimateConfigHelper;
import au.gov.aims.ncanimate.commons.generator.context.GeneratorContext;
import au.gov.aims.ncanimate.commons.generator.context.LayerContext;
import au.gov.aims.ncanimate.commons.timetable.DateTimeRange;
import au.gov.aims.ncanimate.commons.timetable.FrameTimetable;
import au.gov.aims.ncanimate.commons.timetable.FrameTimetableMap;
import au.gov.aims.ncanimate.commons.timetable.NetCDFMetadataFrame;
import au.gov.aims.ncanimate.commons.timetable.NetCDFMetadataSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NcAnimateUtilsTest extends DatabaseTestBase {

    @Before
    public void insertData() throws Exception {
        super.populateDatabase();
    }

    @Test
    public void testParseString() throws Exception {
        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean ncAnimateConfig = configHelper.getNcAnimateConfig("gbr4_v2_temp-wind-salt-current");
        DateTimeZone timezone = NcAnimateUtils.getTimezone(ncAnimateConfig);
        Assert.assertNotNull("NcAnimate configuration not found", ncAnimateConfig);

        DateTime dateFrom = new DateTime(2010, 5, 2, 12, 30, timezone);
        DateTime dateTo = new DateTime(2010, 5, 2, 13, 30, timezone);
        DateTimeRange dateRange = DateTimeRange.create(dateFrom, dateTo);

        Map<String, NcAnimateRegionBean> regions = ncAnimateConfig.getRegions();
        Assert.assertFalse("NcAnimate configuration contains no region", regions == null || regions.isEmpty());

        NcAnimateRegionBean regionBrisbane = regions.get("brisbane");
        NcAnimateRegionBean regionQld = regions.get("qld");

        {
            String pattern = "${id} ${render.scale} ${regions.qld.label} ${targetHeights[0]}";
            String expected = "gbr4_v2_temp-wind-salt-current 1.0 Queensland -12.75";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "Region ID: ${ctx.region.id} label: ${ctx.region.label} height: ${ctx.targetHeight}m";
            String expected = "Region ID: brisbane label: Brisbane height: -12.0m";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "Region ID: ${ctx.region.id} label: ${ctx.region.label} height: ${ctx.targetHeight}m ${ignore.me}";
            String expected = "Region ID: qld label: Queensland height: 2.55m ${ignore.me}";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionQld);
            context.setTargetHeight(2.55);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            List<String> patterns = new ArrayList<String>();
            patterns.add("Region ID: ${ctx.region.id} label: ${ctx.region.label} height: ${ctx.targetHeight}m ${ignore.me}");
            patterns.add("Region ID: ${ctx.region.id} label: ${ctx.region.label} height: ${ctx.targetHeight}m");
            patterns.add("Region not found");

            String expected = "Region ID: qld label: Queensland height: 2.55m";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionQld);
            context.setTargetHeight(2.55);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(patterns, context));
        }

        {
            List<String> patterns = new ArrayList<String>();
            patterns.add("Region ID: ${ctx.region.id} label: ${ctx.region.label} height: ${ctx.targetHeight}m ${ignore.me}");
            patterns.add("Region ID: ${ignore.me} ${ctx.region.id} label: ${ctx.region.label} height: ${ctx.targetHeight}m");
            patterns.add("LAST STRING: ${ignore.me} ${ctx.region.id}");

            String expected = "LAST STRING: ${ignore.me} qld";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionQld);
            context.setTargetHeight(2.55);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(patterns, context));
        }

        {
            String pattern = "Height: ${ctx.targetHeight %.1f}m";
            String expected = "Height: 2.6m";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionQld);
            context.setTargetHeight(2.556254);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "Licensing: ${layers[ereefs-model_gbr4-v2].input.licence}";
            String expected = "Licensing: CC-BY 4.0";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "Licensing: ${panels[temp].layers[ereefs-model_gbr4-v2].input.licence}";
            String expected = "Licensing: CC-BY 4.0";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "Licensing: ${layers.licences}";
            String expected = "Licensing: CC-BY 4.0";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "Data: ${layers[ereefs-model_gbr4-v2].input.authors[0]}. Map generation: AIMS";
            String expected = "Data: eReefs CSIRO GBR4 Hydrodynamic Model v2.0. Map generation: AIMS";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "Data: ${layers.authors}. Map generation: AIMS";
            String expected = "Data: eReefs CSIRO GBR4 Hydrodynamic Model v2.0, eReefs CSIRO GBR4 Bio Geo Chemical Model. Map generation: AIMS";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "${ctx.dateFrom yyyy-MM-dd} - ${ctx.dateTo yyyy-MM-dd}";
            String expected = "2010-05-01 - 2010-05-03";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            context.setDateRange(DateTimeRange.create(
                    new DateTime(2010, 5, 1, 0, 0, timezone),
                    new DateTime(2010, 5, 3, 0, 0, timezone)
            ));

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "${ctx.dateFrom yyyy-MMM-dd} - ${ctx.dateTo yyyy-MMM-dd}";
            String expected = "2010-Dec-01 - 2010-Dec-03";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            context.setDateRange(DateTimeRange.create(
                    new DateTime(2010, 12, 1, 0, 0, timezone),
                    new DateTime(2010, 12, 3, 0, 0, timezone)
            ));

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }
    }

    @Test
    public void testParseStringWithLayerCtx() throws Exception {
        this.insertFakePartialGBR4NetCDFFile();

        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean ncAnimateConfig = configHelper.getNcAnimateConfig("gbr4_v2_temp-wind-salt-current");
        DateTimeZone timezone = NcAnimateUtils.getTimezone(ncAnimateConfig);
        Assert.assertNotNull("NcAnimate configuration not found", ncAnimateConfig);

        DateTime dateFrom = new DateTime(2010, 9, 1, 0, 0, timezone);
        DateTime dateTo =   new DateTime(2010, 9, 1, 1, 0, timezone);
        DateTimeRange dateRange = DateTimeRange.create(dateFrom, dateTo);
        DateTimeRange frameDateRange = dateRange;
        Double closestDepth = -1.5;

        Map<String, NcAnimateRegionBean> regions = ncAnimateConfig.getRegions();
        Assert.assertFalse("NcAnimate configuration contains no region", regions == null || regions.isEmpty());

        NcAnimateRegionBean regionBrisbane = regions.get("brisbane");

        String gbr4LayerId = "ereefs-model_gbr4-v2";
        Map<String, LayerContext> layerContextMap = new HashMap<String, LayerContext>();

        FrameTimetableMap frameTimetableMap = new FrameTimetableMap(ncAnimateConfig, dateRange, this.getDatabaseClient());
        FrameTimetable frameTimetable = frameTimetableMap.get(frameDateRange);
        NetCDFMetadataSet netCDFMetadataSet = frameTimetable.get(gbr4LayerId);
        NetCDFMetadataFrame netCDFMetadataFrame = netCDFMetadataSet.first();

        LayerContext layerContext = new LayerContext(gbr4LayerId, netCDFMetadataFrame, closestDepth);
        layerContextMap.put(gbr4LayerId, layerContext);

        // Test global metadata
        {
            String pattern = "${layerCtx.ereefs-model_gbr4-v2.inputFileMetadata.attributes.title}";
            String expected = "GBR4 Hydro";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context, layerContextMap));
        }

        // Test variable metadata
        {
            String pattern = "${layerCtx.ereefs-model_gbr4-v2.inputFileMetadata.variables.temp.attributes.long_name}";
            String expected = "Temperature";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(dateRange);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context, layerContextMap));
        }
    }

    @Test
    public void testParseOutputFilesString() throws Exception {
        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean ncAnimateConfig = configHelper.getNcAnimateConfig("gbr4_v2_temp-wind-salt-current");
        DateTimeZone timezone = NcAnimateUtils.getTimezone(ncAnimateConfig);
        Assert.assertNotNull("NcAnimate configuration not found", ncAnimateConfig);

        DateTime productDateFrom = new DateTime(2010, 1, 1, 0, 30, timezone);
        DateTime productDateTo = new DateTime(2011, 1, 1, 0, 30, timezone);
        DateTimeRange productDateRange = DateTimeRange.create(productDateFrom, productDateTo);

        Map<String, NcAnimateRegionBean> regions = ncAnimateConfig.getRegions();
        Assert.assertFalse("NcAnimate configuration contains no region", regions == null || regions.isEmpty());

        NcAnimateRegionBean regionBrisbane = regions.get("brisbane");
        NcAnimateRegionBean regionQld = regions.get("qld");

        NcAnimateRenderBean render = ncAnimateConfig.getRender();
        NcAnimateRenderVideoBean renderMp4File = render.getVideos().get("mp4Video");
        NcAnimateRenderVideoBean renderWmvFile = render.getVideos().get("wmvVideo");

        NcAnimateRenderMapBean renderSvgFile = render.getMaps().get("svgMap");

        {
            String pattern = "/usr/bin/ffmpeg -y -r \"${ctx.renderFile.fps}\" -i \"${ctx.videoFrameDirectory}/${ctx.frameFilenamePrefix}_%05d.png\" -vcodec libx264 -profile:v baseline -pix_fmt yuv420p -crf 29 -vf \"pad=${ctx.productWidth}:${ctx.productHeight}:${ctx.padding.left}:${ctx.padding.top}:white\" \"${ctx.outputDirectory}/temp_${ctx.outputFilename}\"";
            String expected = "/usr/bin/ffmpeg -y -r \"12\" -i \"/tmp/ncanimateTests/working/output/frame/gbr4_v2_temp-wind-salt-current/brisbane/height_-12.0/videoFrames/frame_%05d.png\" -vcodec libx264 -profile:v baseline -pix_fmt yuv420p -crf 29 -vf \"pad=2368:832:6:5:white\" \"/tmp/ncanimateTests/working/output/product/gbr4_v2_temp-wind-salt-current/temp_video_2010_brisbane_-12.0.mp4\"";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(productDateRange);
            context.setRegion(regionBrisbane);
            context.setTargetHeight(-12.0);

            context.setRenderFile(renderMp4File);
            context.setOutputFilenamePrefix("video");

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "/usr/bin/ffmpeg -y -r \"${ctx.renderFile.fps}\" -i \"${ctx.videoFrameDirectory}/${ctx.frameFilenamePrefix}_%05d.png\" -qscale 10 -s ${ctx.productWidth}x${ctx.productHeight} \"${ctx.outputFile}\"";
            String expected = "/usr/bin/ffmpeg -y -r \"10\" -i \"/tmp/ncanimateTests/working/output/frame/gbr4_v2_temp-wind-salt-current/qld/height_-5.5/videoFrames/frame_%05d.png\" -qscale 10 -s 1280x447 \"/tmp/ncanimateTests/working/output/product/gbr4_v2_temp-wind-salt-current/video_2010_qld_-5.5.wmv\"";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(productDateRange);
            context.setRegion(regionQld);
            context.setTargetHeight(-5.5);

            context.setRenderFile(renderWmvFile);
            context.setOutputFilenamePrefix("video");

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }

        {
            String pattern = "Map file \"${ctx.outputFile}\" dimensions: ${ctx.productWidth}x${ctx.productHeight}";
            String expected = "Map file \"/tmp/ncanimateTests/working/output/product/gbr4_v2_temp-wind-salt-current/map_2010-01-01T00_30_00.000_10_00_qld_-14.5.svg\" dimensions: 2356x822";

            GeneratorContext context = new GeneratorContext(ncAnimateConfig);
            context.setDateRange(productDateRange);
            context.setRegion(regionQld);
            context.setTargetHeight(-14.5);

            context.setRenderFile(renderSvgFile);
            context.setOutputFilenamePrefix("map");

            Assert.assertEquals(expected, NcAnimateUtils.parseString(pattern, context));
        }
    }
}
