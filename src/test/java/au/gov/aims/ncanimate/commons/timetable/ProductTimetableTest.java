/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.timetable;

import au.gov.aims.ereefs.bean.metadata.TimeIncrement;
import au.gov.aims.ereefs.bean.metadata.TimeIncrementUnit;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateConfigBean;
import au.gov.aims.ereefs.bean.ncanimate.render.AbstractNcAnimateRenderFileBean;
import au.gov.aims.ereefs.database.CacheStrategy;
import au.gov.aims.ereefs.helper.NcAnimateConfigHelper;
import au.gov.aims.ncanimate.commons.DatabaseTestBase;
import au.gov.aims.ncanimate.commons.NcAnimateGenerateFileBean;
import au.gov.aims.ncanimate.commons.NcAnimateUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ProductTimetableTest extends DatabaseTestBase {
    // int hours, int minutes, int seconds, int millis
    private static final Period ONE_HOUR_PERIOD = new Period(1, 0, 0, 0);

    @Before
    public void insertData() throws Exception {
        super.populateDatabase();
    }

    /**
     * Test the ProductTimetable.
     * This test is far from been comprehensive, but it's better than nothing.
     * @throws Exception
     */
    @Test
    public void testProductTimetable() throws Exception {
        this.insertFakePartialGBR4NetCDFFile();

        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean config = configHelper.getNcAnimateConfig("gbr4_v2_temp-wind-salt-current");
        DateTimeZone timezone = NcAnimateUtils.getTimezone(config);

        ProductTimetable productTimetable = new ProductTimetable(config, this.getDatabaseClient());

        Map<DateTimeRange, List<FrameTimetableMap>> mapFrames = productTimetable.getMapFrames();
        Assert.assertNotNull("mapFrames is null", mapFrames);
        Assert.assertEquals("mapFrames size is wrong", 2, mapFrames.size());

        Map<DateTimeRange, List<FrameTimetableMap>> videoFrames = productTimetable.getVideoFrames();
        Assert.assertNotNull("videoFrames is null", videoFrames);
        Assert.assertEquals("videoFrames size is wrong", 1, videoFrames.size());

        // Find the info about the videos for the expected date range
        DateTimeRange videoDateRange = DateTimeRange.create(
            new DateTime(2010, 9, 1, 0, 0, timezone),
            new DateTime(2010, 9, 1, 2, 0, timezone)
        );

        // Find the list of video (wmv, mp4, etc)
        List<FrameTimetableMap> frameTimetableMaps = videoFrames.get(videoDateRange);
        Assert.assertNotNull(String.format("Can not find the FrameTimetableMaps in the videoFrames. Available time ranges: %s", videoFrames.keySet()),
                frameTimetableMaps);
        Assert.assertEquals("Wrong number of FrameTimetableMap in the videoFrames", 1, frameTimetableMaps.size());

        // Check the number of frames for each videos
        for (FrameTimetableMap frameTimetableMap : frameTimetableMaps) {
            Assert.assertNotNull("Can not find the video frames in the FrameTimetableMap", frameTimetableMap);
            Assert.assertEquals("Wrong number of video frames in the FrameTimetableMap", 2, frameTimetableMap.size());
        }
    }

    @Test
    public void testGetOutputFilesYearly() throws Exception {
        super.insertFakeHourlyHourlyData(30);

        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean config = configHelper.getNcAnimateConfig("gbr4_v2_temp-wind-salt-current");
        DateTimeZone timezone = NcAnimateUtils.getTimezone(config);
        //System.out.println(config);

        ProductTimetable productTimetable = new ProductTimetable(config, this.getDatabaseClient());
        Assert.assertNotNull(String.format("The product timetable for %s is null", config.getId()), productTimetable);


        // Verify video output files

        List<NcAnimateGenerateFileBean> outputVideoFiles = productTimetable.getVideoOutputFiles();

        int outputVideoFound = 0;
        for (NcAnimateGenerateFileBean outputVideoFile : outputVideoFiles) {
            String fileId = outputVideoFile.getFileId();

            DateTimeRange dateRange = outputVideoFile.getDateRange();
            DateTime startDate = dateRange == null ? null : dateRange.getStartDate();
            DateTime endDate = dateRange == null ? null : dateRange.getEndDate();

            if ("gbr4_v2_temp-wind-salt-current_video_yearly_2010".equals(fileId)) {
                outputVideoFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 5, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 2, 11, 0, timezone),
                    endDate
                );

                Map<String, AbstractNcAnimateRenderFileBean> renderFiles = outputVideoFile.getRenderFiles();
                Assert.assertNotNull(String.format("Render files is null for file ID %s", fileId), renderFiles);
                int fileFound = 0;
                for (Map.Entry<String, AbstractNcAnimateRenderFileBean> renderFileEntry : renderFiles.entrySet()) {
                    String renderFileId = renderFileEntry.getKey();
                    AbstractNcAnimateRenderFileBean renderFile = renderFileEntry.getValue();
                    if ("mp4Video".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current/gbr4_v2_temp-wind-salt-current_video_yearly_2010_${ctx.region.id}_${ctx.targetHeight}.mp4",
                            renderFile.getFileURI());
                    } else if ("wmvVideo".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current/gbr4_v2_temp-wind-salt-current_video_yearly_2010_${ctx.region.id}_${ctx.targetHeight}.wmv",
                            renderFile.getFileURI());
                    } else {
                        Assert.fail(String.format("Unexpected render file %s for file ID %s", renderFileId, fileId));
                    }
                }
                Assert.assertEquals(String.format("Some output file URIs are missing for file ID: %s", fileId), 2, fileFound);

            } else {
                Assert.fail(String.format("Unexpected file ID: %s", fileId));
            }
        }

        Assert.assertEquals("Wrong number of output video files", 1, outputVideoFiles.size());
        Assert.assertEquals("Some expected output video files are missing", 1, outputVideoFound);


        // Verify map output files

        List<NcAnimateGenerateFileBean> outputMapFiles = productTimetable.getMapOutputFiles();

        int outputMapFound = 0;
        for (NcAnimateGenerateFileBean outputMapFile : outputMapFiles) {
            String fileId = outputMapFile.getFileId();

            if ("gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-01_00h00".equals(fileId) ||
                    "gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-01_01h00".equals(fileId) ||
                    "gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-01_02h00".equals(fileId) ||
                    "gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-01_03h00".equals(fileId) ||
                    "gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-01_04h00".equals(fileId) ||
                    "gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-02_11h00".equals(fileId)) {

                Assert.fail(String.format("Map with no data was found in the list of map to generate: %s", fileId));
            }

            DateTimeRange dateRange = outputMapFile.getDateRange();
            DateTime startDate = dateRange == null ? null : dateRange.getStartDate();
            DateTime endDate = dateRange == null ? null : dateRange.getEndDate();

            // There is 30 files, we will only check a few
            if ("gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-01_05h00".equals(fileId)) {
                outputMapFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 5, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 6, 0, timezone),
                    endDate
                );

                Map<String, AbstractNcAnimateRenderFileBean> renderFiles = outputMapFile.getRenderFiles();
                Assert.assertNotNull(String.format("Render files is null for file ID %s", fileId), renderFiles);
                int fileFound = 0;
                for (Map.Entry<String, AbstractNcAnimateRenderFileBean> renderFileEntry : renderFiles.entrySet()) {
                    String renderFileId = renderFileEntry.getKey();
                    AbstractNcAnimateRenderFileBean renderFile = renderFileEntry.getValue();
                    if ("svgMap".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current/gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-01_05h00_${ctx.region.id}_${ctx.targetHeight}.svg",
                            renderFile.getFileURI());
                    } else if ("pngMap".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current/gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-01_05h00_${ctx.region.id}_${ctx.targetHeight}.png",
                            renderFile.getFileURI());
                    } else {
                        Assert.fail(String.format("Unexpected render file %s for file ID %s", renderFileId, fileId));
                    }
                }
                Assert.assertEquals(String.format("Some output file URIs are missing for file ID: %s", fileId), 2, fileFound);

            } else if ("gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-01_06h00".equals(fileId)) {
                outputMapFound++;

                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 6, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 7, 0, timezone),
                    endDate
                );

                Map<String, AbstractNcAnimateRenderFileBean> renderFiles = outputMapFile.getRenderFiles();
                Assert.assertNotNull(String.format("Render files is null for file ID %s", fileId), renderFiles);
                Assert.assertEquals(String.format("Some output file URIs are missing for file ID: %s", fileId), 2, renderFiles.size());

            } else if ("gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-02_09h00".equals(fileId)) {
                outputMapFound++;

                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 2, 9, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 2, 10, 0, timezone),
                    endDate
                );

                Map<String, AbstractNcAnimateRenderFileBean> renderFiles = outputMapFile.getRenderFiles();
                Assert.assertNotNull(String.format("Render files is null for file ID %s", fileId), renderFiles);
                Assert.assertEquals(String.format("Some output file URIs are missing for file ID: %s", fileId), 2, renderFiles.size());

            } else if ("gbr4_v2_temp-wind-salt-current_map_hourly_2010-09-02_10h00".equals(fileId)) {
                outputMapFound++;

                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 2, 10, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 2, 11, 0, timezone),
                    endDate
                );

                Map<String, AbstractNcAnimateRenderFileBean> renderFiles = outputMapFile.getRenderFiles();
                Assert.assertNotNull(String.format("Render files is null for file ID %s", fileId), renderFiles);
                Assert.assertEquals(String.format("Some output file URIs are missing for file ID: %s", fileId), 2, renderFiles.size());
            }
        }

        Assert.assertEquals("Wrong number of output map files", 30, outputMapFiles.size());
        Assert.assertEquals("Some expected output map files are missing", 4, outputMapFound);
    }

    @Test
    public void testGetOutputFilesMonthly() throws Exception {
        super.insertFakeMonthlyHourlyData(5);

        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean config = configHelper.getNcAnimateConfig("gbr4_v2_temp-wind-salt-current_monthly");
        DateTimeZone timezone = NcAnimateUtils.getTimezone(config);
        //System.out.println(config);

        ProductTimetable productTimetable = new ProductTimetable(config, this.getDatabaseClient());
        Assert.assertNotNull(String.format("The product timetable for %s is null", config.getId()), productTimetable);

        Map<DateTimeRange, List<FrameTimetableMap>> videoFrameMap = productTimetable.getVideoFrames();
        Assert.assertNotNull(String.format("The product timetable for %s video frame collection is null", config.getId()), videoFrameMap);
        Assert.assertFalse(String.format("The product timetable for %s have no video frames", config.getId()), videoFrameMap.isEmpty());

        // Verify map output files

        List<NcAnimateGenerateFileBean> outputMapFiles = productTimetable.getMapOutputFiles();
        Assert.assertTrue("Unexpected output map files", outputMapFiles == null || outputMapFiles.isEmpty());


        // Verify video output files

        List<NcAnimateGenerateFileBean> outputVideoFiles = productTimetable.getVideoOutputFiles();

        int outputVideoFound = 0;

        for (NcAnimateGenerateFileBean outputVideoFile : outputVideoFiles) {
            String fileId = outputVideoFile.getFileId();

            if ("gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2010-08".equals(fileId) ||
                    "gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2011-02".equals(fileId)) {

                Assert.fail(String.format("Video with no data was found in the list of video to generate: %s", fileId));
            }

            DateTimeRange dateRange = outputVideoFile.getDateRange();
            DateTime startDate = dateRange == null ? null : dateRange.getStartDate();
            DateTime endDate = dateRange == null ? null : dateRange.getEndDate();

            List<FrameTimetableMap> videoFrames = videoFrameMap.get(DateTimeRange.create(startDate, endDate));
            Assert.assertNotNull(String.format("Video frame map collection is null for file ID: %s", fileId), videoFrames);
            Assert.assertEquals(String.format("Video frame map collection is doesn't contains the expected number of frame timetable map for file ID: %s", fileId), 1, videoFrames.size());

            FrameTimetableMap videoFrameTimetableMap = videoFrames.get(0);
            Assert.assertNotNull(String.format("Video frame map is null for file ID: %s", fileId), videoFrameTimetableMap);
            Assert.assertFalse(String.format("Video frame map is empty for file ID: %s", fileId), videoFrameTimetableMap.isEmpty());

            if ("gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2010-09".equals(fileId)) {
                outputVideoFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 0, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 10, 1, 0, 0, timezone),
                    endDate
                );

                // 30 days of 24 hours, starting at 5:00 am
                Assert.assertEquals(String.format("Wrong number of video frame for file ID: %s", fileId), 30*24, videoFrameTimetableMap.size());

                Map<String, AbstractNcAnimateRenderFileBean> renderFiles = outputVideoFile.getRenderFiles();
                Assert.assertNotNull(String.format("Render files is null for file ID %s", fileId), renderFiles);
                int fileFound = 0;
                for (Map.Entry<String, AbstractNcAnimateRenderFileBean> renderFileEntry : renderFiles.entrySet()) {
                    String renderFileId = renderFileEntry.getKey();
                    AbstractNcAnimateRenderFileBean renderFile = renderFileEntry.getValue();
                    if ("mp4Video".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current_monthly/gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2010-09_${ctx.region.id}_${ctx.targetHeight}.mp4",
                            renderFile.getFileURI());

                    } else if ("wmvVideo".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current_monthly/gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2010-09_${ctx.region.id}_${ctx.targetHeight}.wmv",
                            renderFile.getFileURI());
                    } else {
                        Assert.fail(String.format("Unexpected render file %s for file ID %s", renderFileId, fileId));
                    }
                }

                Assert.assertEquals(String.format("Some output file URIs are missing for file ID: %s", fileId), 2, fileFound);

            } else if ("gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2010-10".equals(fileId)) {
                outputVideoFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 10, 1, 0, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 11, 1, 0, 0, timezone),
                    endDate
                );

                // 30 days of 24 hours, starting at 5:00 am
                Assert.assertEquals(String.format("Wrong number of video frame for file ID: %s", fileId), 31*24, videoFrameTimetableMap.size());

            } else if ("gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2010-11".equals(fileId)) {
                outputVideoFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 11, 1, 0, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 12, 1, 0, 0, timezone),
                    endDate
                );

                // 30 days of 24 hours, starting at 5:00 am
                Assert.assertEquals(String.format("Wrong number of video frame for file ID: %s", fileId), 30*24, videoFrameTimetableMap.size());

            } else if ("gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2010-12".equals(fileId)) {
                outputVideoFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 12, 1, 0, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2011, 1, 1, 0, 0, timezone),
                    endDate
                );

                // 30 days of 24 hours, starting at 5:00 am
                Assert.assertEquals(String.format("Wrong number of video frame for file ID: %s", fileId), 31*24, videoFrameTimetableMap.size());

            } else if ("gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2011-01".equals(fileId)) {
                outputVideoFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2011, 1, 1, 0, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2011, 2, 1, 0, 0, timezone),
                    endDate
                );

                // 31 days of 24 hours
                Assert.assertEquals(String.format("Wrong number of video frame for file ID: %s", fileId), 31*24, videoFrameTimetableMap.size());

                Map<String, AbstractNcAnimateRenderFileBean> renderFiles = outputVideoFile.getRenderFiles();
                Assert.assertNotNull(String.format("Render files is null for file ID %s", fileId), renderFiles);
                int fileFound = 0;
                for (Map.Entry<String, AbstractNcAnimateRenderFileBean> renderFileEntry : renderFiles.entrySet()) {
                    String renderFileId = renderFileEntry.getKey();
                    AbstractNcAnimateRenderFileBean renderFile = renderFileEntry.getValue();
                    if ("mp4Video".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current_monthly/gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2011-01_${ctx.region.id}_${ctx.targetHeight}.mp4",
                            renderFile.getFileURI());
                    } else if ("wmvVideo".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current_monthly/gbr4_v2_temp-wind-salt-current_monthly_video_monthly_2011-01_${ctx.region.id}_${ctx.targetHeight}.wmv",
                            renderFile.getFileURI());
                    } else {
                        Assert.fail(String.format("Unexpected render file %s for file ID %s", renderFileId, fileId));
                    }
                }
                Assert.assertEquals(String.format("Some output file URIs are missing for file ID: %s", fileId), 2, fileFound);

            } else {
                Assert.fail(String.format("Unexpected file ID: %s", fileId));
            }
        }

        Assert.assertEquals("Wrong number of output files", 5, outputVideoFiles.size());
        Assert.assertEquals("Some expected output files are missing", 5, outputVideoFound);
    }

    @Test
    public void testGetOutputFilesHourly() throws Exception {
        super.insertFakeHourlyHourlyData(30);

        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean config = configHelper.getNcAnimateConfig("gbr4_v2_temp-wind-salt-current_maps");
        DateTimeZone timezone = NcAnimateUtils.getTimezone(config);

        ProductTimetable productTimetable = new ProductTimetable(config, this.getDatabaseClient());
        Assert.assertNotNull(String.format("The product timetable for %s is null", config.getId()), productTimetable);


        // Verify video output files

        List<NcAnimateGenerateFileBean> outputVideoFiles = productTimetable.getVideoOutputFiles();
        Assert.assertTrue("Unexpected output video files", outputVideoFiles == null || outputVideoFiles.isEmpty());


        // Verify map output files

        List<NcAnimateGenerateFileBean> outputMapFiles = productTimetable.getMapOutputFiles();

        int outputFound = 0;
        for (NcAnimateGenerateFileBean outputMapFile : outputMapFiles) {
            String fileId = outputMapFile.getFileId();

            if ("gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_02h00".equals(fileId) ||
                    "gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_03h00".equals(fileId) ||
                    "gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_04h00".equals(fileId) ||
                    "gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-02_11h00".equals(fileId)) {

                Assert.fail(String.format("Product with no data was found in the list of product to generate: %s", fileId));
            }

            DateTimeRange dateRange = outputMapFile.getDateRange();
            DateTime startDate = dateRange == null ? null : dateRange.getStartDate();
            DateTime endDate = dateRange == null ? null : dateRange.getEndDate();

            if ("gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_05h00".equals(fileId)) {
                outputFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 5, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 6, 0, timezone),
                    endDate
                );

                Map<String, AbstractNcAnimateRenderFileBean> renderFiles = outputMapFile.getRenderFiles();
                Assert.assertNotNull(String.format("Render files is null for file ID %s", fileId), renderFiles);
                int fileFound = 0;
                for (Map.Entry<String, AbstractNcAnimateRenderFileBean> renderFileEntry : renderFiles.entrySet()) {
                    String renderFileId = renderFileEntry.getKey();
                    AbstractNcAnimateRenderFileBean renderFile = renderFileEntry.getValue();
                    if ("pngMap".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current_maps/gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_05h00_${ctx.region.id}_${ctx.targetHeight}.png",
                            renderFile.getFileURI().toString());
                    } else if ("svgMap".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current_maps/gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_05h00_${ctx.region.id}_${ctx.targetHeight}.svg",
                            renderFile.getFileURI().toString());
                    } else {
                        Assert.fail(String.format("Unexpected render file %s for file ID %s", renderFileId, fileId));
                    }
                }
                Assert.assertEquals(String.format("Some output file URIs are missing for file ID: %s", fileId), 2, fileFound);

            } else if ("gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_06h00".equals(fileId)) {
                outputFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 6, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 7, 0, timezone),
                    endDate
                );

                Map<String, AbstractNcAnimateRenderFileBean> renderFiles = outputMapFile.getRenderFiles();
                Assert.assertNotNull(String.format("Render files is null for file ID %s", fileId), renderFiles);
                int fileFound = 0;
                for (Map.Entry<String, AbstractNcAnimateRenderFileBean> renderFileEntry : renderFiles.entrySet()) {
                    String renderFileId = renderFileEntry.getKey();
                    AbstractNcAnimateRenderFileBean renderFile = renderFileEntry.getValue();
                    if ("pngMap".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current_maps/gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_06h00_${ctx.region.id}_${ctx.targetHeight}.png",
                            renderFile.getFileURI().toString());
                    } else if ("svgMap".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current_maps/gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_06h00_${ctx.region.id}_${ctx.targetHeight}.svg",
                            renderFile.getFileURI().toString());
                    } else {
                        Assert.fail(String.format("Unexpected render file %s for file ID %s", renderFileId, fileId));
                    }
                }
                Assert.assertEquals(String.format("Some output file URIs are missing for file ID: %s", fileId), 2, fileFound);

            } else if ("gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_07h00".equals(fileId)) {

                outputFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 7, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 8, 0, timezone),
                    endDate
                );
            // ...

            } else if ("gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-01_23h00".equals(fileId)) {
                outputFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 1, 23, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 2, 0, 0, timezone),
                    endDate
                );

            } else if ("gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-02_00h00".equals(fileId)) {
                outputFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 2, 0, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 2, 1, 0, timezone),
                    endDate
                );

            // ...

            } else if ("gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-02_10h00".equals(fileId)) {
                outputFound++;
                Assert.assertEquals(
                    String.format("Wrong start date for file ID %s", fileId),
                    new DateTime(2010, 9, 2, 10, 0, timezone),
                    startDate
                );
                Assert.assertEquals(
                    String.format("Wrong end date for file ID %s", fileId),
                    new DateTime(2010, 9, 2, 11, 0, timezone),
                    endDate
                );

                Map<String, AbstractNcAnimateRenderFileBean> renderFiles = outputMapFile.getRenderFiles();
                Assert.assertNotNull(String.format("Render files is null for file ID %s", fileId), renderFiles);
                int fileFound = 0;
                for (Map.Entry<String, AbstractNcAnimateRenderFileBean> renderFileEntry : renderFiles.entrySet()) {
                    String renderFileId = renderFileEntry.getKey();
                    AbstractNcAnimateRenderFileBean renderFile = renderFileEntry.getValue();
                    if ("pngMap".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current_maps/gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-02_10h00_${ctx.region.id}_${ctx.targetHeight}.png",
                            renderFile.getFileURI().toString());
                    } else if ("svgMap".equals(renderFileId)) {
                        fileFound++;
                        Assert.assertEquals(
                            String.format("Wrong file URI for file ID %s", fileId),
                            "/home/ereefs/derived/ncanimate/products/gbr4_v2_temp-wind-salt-current_maps/gbr4_v2_temp-wind-salt-current_maps_map_hourly_2010-09-02_10h00_${ctx.region.id}_${ctx.targetHeight}.svg",
                            renderFile.getFileURI().toString());
                    } else {
                        Assert.fail(String.format("Unexpected render file %s for file ID %s", renderFileId, fileId));
                    }
                }
                Assert.assertEquals(String.format("Some output file URIs are missing for file ID: %s", fileId), 2, fileFound);
            }
        }

        Assert.assertEquals("Wrong number of output files", 30, outputMapFiles.size());
        Assert.assertEquals("Some expected output files are missing", 6, outputFound);
    }


    /**
     * IMOS is not on the same time period, which may creates
     * empty product (i.e video with no frames).
     * This should not happen.
     */
    @Test
    public void testNoEmptyProductGBR4() throws Exception {
        super.insertFakeGBR4DailyDailyData(5);

        DateTimeZone qldTimezone = DateTimeZone.forID("Australia/Brisbane");

        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean config = configHelper.getNcAnimateConfig("ereefs-temperature");
        //System.out.println(config);

        ProductTimetable productTimetable = new ProductTimetable(config, this.getDatabaseClient());
        Assert.assertNotNull(String.format("The product timetable for %s is null", config.getId()), productTimetable);

        // Validate time increment.
        // Expecting: 1 DAY
        TimeIncrement frameTimeIncrement = config.getFrameTimeIncrement();
        Assert.assertNotNull("Frame time increment is null", frameTimeIncrement);
        Assert.assertNotNull("Frame time increment value is null", frameTimeIncrement.getIncrement());
        Assert.assertNotNull("Frame time increment unit is null", frameTimeIncrement.getUnit());
        Assert.assertEquals("Wrong frame time increment value", 1, frameTimeIncrement.getSafeIncrement());
        Assert.assertEquals("Wrong frame time increment unit", TimeIncrementUnit.DAY, frameTimeIncrement.getSafeUnit());

        Map<DateTimeRange, List<FrameTimetableMap>> videoFrameMap = productTimetable.getVideoFrames();

        // Check for empty frame (should be none)
        for (Map.Entry<DateTimeRange, List<FrameTimetableMap>> entry : videoFrameMap.entrySet()) {
            for (FrameTimetableMap frameTimetableMap : entry.getValue()) {
                for (Map.Entry<DateTimeRange, FrameTimetable> frameTimetableEntry : frameTimetableMap.entrySet()) {
                    Assert.assertFalse(
                        String.format("Frame time table is empty for date range: %s", frameTimetableEntry.getKey()),
                        frameTimetableEntry.getValue().isEmpty());
                }
            }
        }

        // Check for missing frame
        // NOTE: Use TreeSet instead of HashSet to get more user friendly error message from JUnit.
        //     It easier to read when the dates are in chronological order.
        Set<DateTimeRange> expectedFrames = new TreeSet<DateTimeRange>();
        expectedFrames.add(DateTimeRange.create(new DateTime(2010, 9, 1, 0, 0, qldTimezone), new DateTime(2010, 9, 2, 0, 0, qldTimezone)));
        expectedFrames.add(DateTimeRange.create(new DateTime(2010, 9, 2, 0, 0, qldTimezone), new DateTime(2010, 9, 3, 0, 0, qldTimezone)));
        expectedFrames.add(DateTimeRange.create(new DateTime(2010, 9, 3, 0, 0, qldTimezone), new DateTime(2010, 9, 4, 0, 0, qldTimezone)));
        expectedFrames.add(DateTimeRange.create(new DateTime(2010, 9, 4, 0, 0, qldTimezone), new DateTime(2010, 9, 5, 0, 0, qldTimezone)));
        expectedFrames.add(DateTimeRange.create(new DateTime(2010, 9, 5, 0, 0, qldTimezone), new DateTime(2010, 9, 6, 0, 0, qldTimezone)));

        for (Map.Entry<DateTimeRange, List<FrameTimetableMap>> entry : videoFrameMap.entrySet()) {
            for (FrameTimetableMap frameTimetableMap : entry.getValue()) {
                Assert.assertEquals("Wrong frame date time ranges", expectedFrames, frameTimetableMap.keySet());
            }
        }
    }

    @Test
    public void testNoEmptyProductIMOS() throws Exception {
        super.insertFakeGBR4DailyDailyData(5);
        super.insertFakeIMOSDailyDailyData(5);

        DateTimeZone qldTimezone = DateTimeZone.forID("Australia/Brisbane");

        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean config = configHelper.getNcAnimateConfig("imos-vs-ereefs-temperature");
        //System.out.println(config);

        ProductTimetable productTimetable = new ProductTimetable(config, this.getDatabaseClient());
        Assert.assertNotNull(String.format("The product timetable for %s is null", config.getId()), productTimetable);

        // Validate time increment.
        // Expecting: 1 DAY
        TimeIncrement frameTimeIncrement = config.getFrameTimeIncrement();
        Assert.assertNotNull("Frame time increment is null", frameTimeIncrement);
        Assert.assertNotNull("Frame time increment value is null", frameTimeIncrement.getIncrement());
        Assert.assertNotNull("Frame time increment unit is null", frameTimeIncrement.getUnit());
        Assert.assertEquals("Wrong frame time increment value", 1, frameTimeIncrement.getSafeIncrement());
        Assert.assertEquals("Wrong frame time increment unit", TimeIncrementUnit.DAY, frameTimeIncrement.getSafeUnit());

        Map<DateTimeRange, List<FrameTimetableMap>> videoFrameMap = productTimetable.getVideoFrames();

        // Check for empty frame (should be none)
        for (Map.Entry<DateTimeRange, List<FrameTimetableMap>> entry : videoFrameMap.entrySet()) {
            for (FrameTimetableMap frameTimetableMap : entry.getValue()) {
                for (Map.Entry<DateTimeRange, FrameTimetable> frameTimetableEntry : frameTimetableMap.entrySet()) {
                    Assert.assertFalse(
                        String.format("Frame time table is empty for date range: %s", frameTimetableEntry.getKey()),
                        frameTimetableEntry.getValue().isEmpty());
                }
            }
        }

        // Check for missing frame
        // NOTE: Use TreeSet instead of HashSet to get more user friendly error message from JUnit.
        //     It easier to read when the dates are in chronological order.
        Set<DateTimeRange> expectedFrames = new TreeSet<DateTimeRange>();
        expectedFrames.add(DateTimeRange.create(new DateTime(2010, 9, 1, 0, 0, qldTimezone), new DateTime(2010, 9, 2, 0, 0, qldTimezone)));
        expectedFrames.add(DateTimeRange.create(new DateTime(2010, 9, 2, 0, 0, qldTimezone), new DateTime(2010, 9, 3, 0, 0, qldTimezone)));
        expectedFrames.add(DateTimeRange.create(new DateTime(2010, 9, 3, 0, 0, qldTimezone), new DateTime(2010, 9, 4, 0, 0, qldTimezone)));
        expectedFrames.add(DateTimeRange.create(new DateTime(2010, 9, 4, 0, 0, qldTimezone), new DateTime(2010, 9, 5, 0, 0, qldTimezone)));
        expectedFrames.add(DateTimeRange.create(new DateTime(2010, 9, 5, 0, 0, qldTimezone), new DateTime(2010, 9, 6, 0, 0, qldTimezone)));

        for (Map.Entry<DateTimeRange, List<FrameTimetableMap>> entry : videoFrameMap.entrySet()) {
            for (FrameTimetableMap frameTimetableMap : entry.getValue()) {
                Assert.assertEquals("Wrong frame date time ranges", expectedFrames, frameTimetableMap.keySet());
            }
        }
    }



    @Test
    public void testTimetableMapFromIrregularDatesWithoutFocusLayers() throws Exception {
        DateTimeZone qldTimezone = DateTimeZone.forID("Australia/Brisbane");
        /*
         * Data:
         * - GBR4: [ 1   2   3   4 ]           [ 8 ]
         * - GBR1:         [ 3   4   5   6 ]       [ 9 ]
         *   2010    1   2   3   4   5   6   7   8   9   10   11   12
         *
         * Expect product timetable
         *   2010  [ 1   2   3   4   5   6 ] 7 [ 8   9 ] 10   11   12
         */
        this.insertFakeMonthlyHourlyData("downloads/gbr4_v2", "gbr4_v2", new DateTime(2010, 1, 1, 0, 0, qldTimezone), 4);
        this.insertFakeMonthlyHourlyData("downloads/gbr4_v2", "gbr4_v2", new DateTime(2010, 8, 1, 0, 0, qldTimezone), 1);
        this.insertFakeMonthlyHourlyData("downloads/gbr1_2-0", "gbr1_2-0", new DateTime(2010, 3, 1, 0, 0, qldTimezone), 4);
        this.insertFakeMonthlyHourlyData("downloads/gbr1_2-0", "gbr1_2-0", new DateTime(2010, 9, 1, 0, 0, qldTimezone), 1);

        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean config = configHelper.getNcAnimateConfig("gbr4_gbr1_temp-wind-salt-current_without-focus");

        ProductTimetable productTimetable = new ProductTimetable(config, this.getDatabaseClient());
        Assert.assertNotNull(String.format("The product timetable for %s is null", config.getId()), productTimetable);

        Assert.assertNull("Unexpected map time increment", productTimetable.getMapTimeIncrement());
        Assert.assertEquals("Wrong video time increment",
                new TimeIncrement(1, TimeIncrementUnit.MONTH), productTimetable.getVideoTimeIncrement());

        int found = 0;
        for (Map.Entry<DateTimeRange, List<FrameTimetableMap>> videoFrames : productTimetable.getVideoFrames().entrySet()) {
            DateTimeRange videoTimeRange = videoFrames.getKey();
            switch(videoTimeRange.toString()) {
                case "2010-01-01T00:00:00.000+10:00 - 2010-02-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr4-v2");
                    found++;
                    break;

                case "2010-02-01T00:00:00.000+10:00 - 2010-03-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr4-v2");
                    found++;
                    break;

                case "2010-03-01T00:00:00.000+10:00 - 2010-04-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr4-v2", "ereefs-model_gbr1-v2");
                    found++;
                    break;

                case "2010-04-01T00:00:00.000+10:00 - 2010-05-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr4-v2", "ereefs-model_gbr1-v2");
                    found++;
                    break;

                case "2010-05-01T00:00:00.000+10:00 - 2010-06-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr1-v2");
                    found++;
                    break;

                case "2010-06-01T00:00:00.000+10:00 - 2010-07-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr1-v2");
                    found++;
                    break;

                case "2010-08-01T00:00:00.000+10:00 - 2010-09-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr4-v2");
                    found++;
                    break;

                case "2010-09-01T00:00:00.000+10:00 - 2010-10-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr1-v2");
                    found++;
                    break;

                default:
                    Assert.fail(String.format("Unexpected video time range: %s", videoTimeRange));
            }
        }

        Assert.assertEquals("Some videos are missing", 8, found);
    }

    @Test
    public void testTimetableMapFromIrregularDatesWithFocusLayers() throws Exception {
        DateTimeZone qldTimezone = DateTimeZone.forID("Australia/Brisbane");
        /*
         * Data:
         * - GBR4: [ 1   2   3   4 ]           [ 8 ]
         * - GBR1:         [ 3   4   5   6 ]       [ 9 ]
         *   2010    1   2   3   4   5   6   7   8   9  10  11  12
         *
         * Expect product timetable
         *   2010          [ 3   4   5   6 ]       [ 9 ]
         */
        this.insertFakeMonthlyHourlyData("downloads/gbr4_v2", "gbr4_v2", new DateTime(2010, 1, 1, 0, 0, qldTimezone), 4);
        this.insertFakeMonthlyHourlyData("downloads/gbr4_v2", "gbr4_v2", new DateTime(2010, 8, 1, 0, 0, qldTimezone), 1);
        this.insertFakeMonthlyHourlyData("downloads/gbr1_2-0", "gbr1_2-0", new DateTime(2010, 3, 1, 0, 0, qldTimezone), 4);
        this.insertFakeMonthlyHourlyData("downloads/gbr1_2-0", "gbr1_2-0", new DateTime(2010, 9, 1, 0, 0, qldTimezone), 1);

        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean config = configHelper.getNcAnimateConfig("gbr4_gbr1_temp-wind-salt-current_with-focus");

        ProductTimetable productTimetable = new ProductTimetable(config, this.getDatabaseClient());
        Assert.assertNotNull(String.format("The product timetable for %s is null", config.getId()), productTimetable);

        Assert.assertNull("Unexpected map time increment", productTimetable.getMapTimeIncrement());
        Assert.assertEquals("Wrong video time increment",
                new TimeIncrement(1, TimeIncrementUnit.MONTH), productTimetable.getVideoTimeIncrement());

        int found = 0;
        for (Map.Entry<DateTimeRange, List<FrameTimetableMap>> videoFrames : productTimetable.getVideoFrames().entrySet()) {
            DateTimeRange videoTimeRange = videoFrames.getKey();
            switch(videoTimeRange.toString()) {
                case "2010-03-01T00:00:00.000+10:00 - 2010-04-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr4-v2", "ereefs-model_gbr1-v2");
                    found++;
                    break;

                case "2010-04-01T00:00:00.000+10:00 - 2010-05-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr4-v2", "ereefs-model_gbr1-v2");
                    found++;
                    break;

                case "2010-05-01T00:00:00.000+10:00 - 2010-06-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr1-v2");
                    found++;
                    break;

                case "2010-06-01T00:00:00.000+10:00 - 2010-07-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr1-v2");
                    found++;
                    break;

                case "2010-09-01T00:00:00.000+10:00 - 2010-10-01T00:00:00.000+10:00":
                    this.testVideoFrames(videoTimeRange, videoFrames.getValue(), "ereefs-model_gbr1-v2");
                    found++;
                    break;

                default:
                    Assert.fail(String.format("Unexpected video time range: %s", videoTimeRange));
            }
        }

        Assert.assertEquals("Some videos are missing", 5, found);
    }

    private void testVideoFrames(DateTimeRange videoTimeRange, List<FrameTimetableMap> videoFrames, String ... expectedSources) {
        Set<String> expectedSourceSet = new HashSet<String>();
        Collections.addAll(expectedSourceSet, expectedSources);

        for (FrameTimetableMap frameTimetableMap : videoFrames) {
            for (Map.Entry<DateTimeRange, FrameTimetable> frameTimetableEntry : frameTimetableMap.entrySet()) {
                DateTimeRange frameTimeRange = frameTimetableEntry.getKey();

                // frameTimeRange == 1 hour
                Period diff = new Period(frameTimeRange.getStartDate(), frameTimeRange.getEndDate());
                Assert.assertEquals("Frame is not hourly", diff, ONE_HOUR_PERIOD);

                // frameTimeRange.getStartDate() >= videoTimeRange.getStartDate()
                Assert.assertTrue("Frame start is out of bounds", frameTimeRange.getStartDate().compareTo(videoTimeRange.getStartDate()) >= 0);

                // frameTimeRange.getEndDate() <= videoTimeRange.getEndDate()
                Assert.assertTrue("Frame end is out of bounds", frameTimeRange.getEndDate().compareTo(videoTimeRange.getEndDate()) <= 0);

                for (Map.Entry<String, NetCDFMetadataSet> frameEntry : frameTimetableEntry.getValue().entrySet()) {
                    String sourceId = frameEntry.getKey();
                    Assert.assertTrue(String.format("Unexpected input ID %s for frame date %s", sourceId, frameTimeRange),
                            expectedSourceSet.contains(sourceId));

                    NetCDFMetadataSet metadataFrames = frameEntry.getValue();
                    Assert.assertEquals(String.format("Unexpected number of NetCDF metadata found for input ID %s for frame date %s", sourceId, frameTimeRange),
                            1, metadataFrames.size());

                    for (NetCDFMetadataFrame metadataFrame : metadataFrames) {
                        DateTime metadataFrameDate = metadataFrame.getFrameDateTime();

                        Assert.assertEquals(String.format("Unexpected frame date for input ID %s for frame date %s", sourceId, frameTimeRange),
                                frameTimeRange.getStartDate(), metadataFrameDate);
                    }
                }
            }
        }
    }
}
