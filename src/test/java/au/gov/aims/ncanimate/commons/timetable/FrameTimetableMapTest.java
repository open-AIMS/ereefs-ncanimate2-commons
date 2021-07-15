/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.timetable;

import au.gov.aims.ereefs.bean.metadata.netcdf.NetCDFMetadataBean;
import au.gov.aims.ereefs.bean.ncanimate.NcAnimateConfigBean;
import au.gov.aims.ereefs.database.CacheStrategy;
import au.gov.aims.ereefs.helper.NcAnimateConfigHelper;
import au.gov.aims.ncanimate.commons.DatabaseTestBase;
import au.gov.aims.ncanimate.commons.NcAnimateUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class FrameTimetableMapTest extends DatabaseTestBase {

    @Before
    public void insertData() throws Exception {
        super.populateDatabase();
    }

    @Test
    public void testTimetableMapFromRegularDates() throws Exception {
        super.insertFakeHourlyHourlyData(30);
        super.insertFakePartialGBR4NetCDFFile();

        NcAnimateConfigHelper configHelper = new NcAnimateConfigHelper(this.getDatabaseClient(), CacheStrategy.DISK);
        NcAnimateConfigBean config = configHelper.getNcAnimateConfig("gbr4_v2_temp-wind-salt-current");
        DateTimeZone timezone = NcAnimateUtils.getTimezone(config);

        DateTime firstDate = new DateTime(2010, 9, 1, 0, 0, 0, 0, timezone);
        DateTime lastDate = new DateTime(2010, 9, 6, 0, 0, 0, 0, timezone);

        FrameTimetableMap timetableMap = new FrameTimetableMap(config, DateTimeRange.create(firstDate, lastDate), this.getDatabaseClient());

        int frameTimetableFound = 0;
        for (Map.Entry<DateTimeRange, FrameTimetable> timetableEntry : timetableMap.entrySet()) {
            DateTimeRange dateRange = timetableEntry.getKey();
            FrameTimetable frameTimetable = timetableEntry.getValue();

            if (firstDate.compareTo(dateRange.getStartDate()) > 0 ||
                    lastDate.compareTo(dateRange.getEndDate()) < 0) {

                Assert.fail(String.format("Frame timetable out of range. Expected values between [%s - %s]. Found: [%s]",
                        firstDate, lastDate, dateRange));
            }

            // There is over 100 entries. Check only a few
            if (dateRange.getStartDate().equals(new DateTime(2010, 9, 1, 0, 0, 0, 0, timezone))) {
                frameTimetableFound++;
                // "metadata": "downloads/gbr4_v2/gbr4_v2_2010-09-01_00h00-02h00.nc",
                // "frameDateTime": "2010-09-01T00:00:00.000+10:00"

                Assert.assertEquals(String.format("Wrong end date for [%s]", dateRange),
                        new DateTime(2010, 9, 1, 1, 0, 0, 0, timezone), dateRange.getEndDate());

                Assert.assertEquals(String.format("Wrong number of NetCDFMetadataSet for [%s]", dateRange),
                        1, frameTimetable.size());

                NetCDFMetadataSet metadataSet = frameTimetable.get("ereefs-model_gbr4-v2");
                Assert.assertNotNull(String.format("NetCDFMetadataSet id ereefs-model_gbr4-v2 was not found in [%s]", dateRange),
                        metadataSet);

                NetCDFMetadataFrame metadataFrame = metadataSet.first();
                Assert.assertNotNull(String.format("NetCDFMetadataFrame was not found in [%s]", dateRange),
                        metadataFrame);

                Assert.assertEquals("Wrong NetCDFMetadataFrame date", dateRange.getStartDate(), metadataFrame.getFrameDateTime());
                NetCDFMetadataBean metadata = metadataFrame.getMetadata();
                Assert.assertNotNull(String.format("NetCDFMetadataBean was not found in [%s]", dateRange),
                        metadata);

                Assert.assertEquals("Wrong NetCDFMetadataBean id", "downloads/gbr4_v2/gbr4_v2_2010-09-01_00h00-02h00_nc", metadata.getId());

            } else if (dateRange.getStartDate().equals(new DateTime(2010, 9, 1, 1, 0, 0, 0, timezone))) {
                frameTimetableFound++;
                // "metadata": "downloads/gbr4_v2/gbr4_v2_2010-09-01_00h00-02h00.nc",
                // "frameDateTime": "2010-09-01T01:00:00.000+10:00"

                Assert.assertEquals(String.format("Wrong end date for [%s]", dateRange),
                        new DateTime(2010, 9, 1, 2, 0, 0, 0, timezone), dateRange.getEndDate());

                Assert.assertEquals(String.format("Wrong number of NetCDFMetadataSet for [%s]", dateRange),
                        1, frameTimetable.size());

                NetCDFMetadataSet metadataSet = frameTimetable.get("ereefs-model_gbr4-v2");
                Assert.assertNotNull(String.format("NetCDFMetadataSet id ereefs-model_gbr4-v2 was not found in [%s]", dateRange),
                        metadataSet);

                NetCDFMetadataFrame metadataFrame = metadataSet.first();
                Assert.assertNotNull(String.format("NetCDFMetadataFrame was not found in [%s]", dateRange),
                        metadataFrame);

                Assert.assertEquals("Wrong NetCDFMetadataFrame date", dateRange.getStartDate(), metadataFrame.getFrameDateTime());
                NetCDFMetadataBean metadata = metadataFrame.getMetadata();
                Assert.assertNotNull(String.format("NetCDFMetadataBean was not found in [%s]", dateRange),
                        metadata);

                Assert.assertEquals("Wrong NetCDFMetadataBean id", "downloads/gbr4_v2/gbr4_v2_2010-09-01_00h00-02h00_nc", metadata.getId());

            } else if (dateRange.getStartDate().equals(new DateTime(2010, 9, 1, 2, 0, 0, 0, timezone))) {
                frameTimetableFound++;
                // Empty

                Assert.assertEquals(String.format("Wrong end date for [%s]", dateRange),
                        new DateTime(2010, 9, 1, 3, 0, 0, 0, timezone), dateRange.getEndDate());

                Assert.assertEquals(String.format("Wrong number of NetCDFMetadataSet for [%s]", dateRange),
                        0, frameTimetable.size());

            } else if (dateRange.getStartDate().equals(new DateTime(2010, 9, 1, 4, 0, 0, 0, timezone))) {
                frameTimetableFound++;
                // Empty

                Assert.assertEquals(String.format("Wrong end date for [%s]", dateRange),
                        new DateTime(2010, 9, 1, 5, 0, 0, 0, timezone), dateRange.getEndDate());

                Assert.assertEquals(String.format("Wrong number of NetCDFMetadataSet for [%s]", dateRange),
                        0, frameTimetable.size());

            } else if (dateRange.getStartDate().equals(new DateTime(2010, 9, 1, 5, 0, 0, 0, timezone))) {
                frameTimetableFound++;
                // "metadata": "downloads/gbr4_v2/FAKE_gbr4_v2_0_2010-09-01_05h00.nc",
                // "frameDateTime": "2010-09-01T05:00:00.000+10:00"

                Assert.assertEquals(String.format("Wrong end date for [%s]", dateRange),
                        new DateTime(2010, 9, 1, 6, 0, 0, 0, timezone), dateRange.getEndDate());

                Assert.assertEquals(String.format("Wrong number of NetCDFMetadataSet for [%s]", dateRange),
                        1, frameTimetable.size());

                NetCDFMetadataSet metadataSet = frameTimetable.get("ereefs-model_gbr4-v2");
                Assert.assertNotNull(String.format("NetCDFMetadataSet id ereefs-model_gbr4-v2 was not found in [%s]", dateRange),
                        metadataSet);

                NetCDFMetadataFrame metadataFrame = metadataSet.first();
                Assert.assertNotNull(String.format("NetCDFMetadataFrame was not found in [%s]", dateRange),
                        metadataFrame);

                Assert.assertEquals("Wrong NetCDFMetadataFrame date", dateRange.getStartDate(), metadataFrame.getFrameDateTime());
                NetCDFMetadataBean metadata = metadataFrame.getMetadata();
                Assert.assertNotNull(String.format("NetCDFMetadataBean was not found in [%s]", dateRange),
                        metadata);

                Assert.assertEquals("Wrong NetCDFMetadataBean id", "downloads/gbr4_v2/FAKE_gbr4_v2_0_2010-09-01_05h00_nc", metadata.getId());

            // ...

            } else if (dateRange.getStartDate().equals(new DateTime(2010, 9, 2, 10, 0, 0, 0, timezone))) {
                frameTimetableFound++;
                // "metadata": "downloads/gbr4_v2/FAKE_gbr4_v2_29_2010-09-02_10h00.nc",
                // "frameDateTime": "2010-09-02T10:00:00.000+10:00"

                Assert.assertEquals(String.format("Wrong end date for [%s]", dateRange),
                        new DateTime(2010, 9, 2, 11, 0, 0, 0, timezone), dateRange.getEndDate());

                Assert.assertEquals(String.format("Wrong number of NetCDFMetadataSet for [%s]", dateRange),
                        1, frameTimetable.size());

                NetCDFMetadataSet metadataSet = frameTimetable.get("ereefs-model_gbr4-v2");
                Assert.assertNotNull(String.format("NetCDFMetadataSet id ereefs-model_gbr4-v2 was not found in [%s]", dateRange),
                        metadataSet);

                NetCDFMetadataFrame metadataFrame = metadataSet.first();
                Assert.assertNotNull(String.format("NetCDFMetadataFrame was not found in [%s]", dateRange),
                        metadataFrame);

                Assert.assertEquals("Wrong NetCDFMetadataFrame date", dateRange.getStartDate(), metadataFrame.getFrameDateTime());
                NetCDFMetadataBean metadata = metadataFrame.getMetadata();
                Assert.assertNotNull(String.format("NetCDFMetadataBean was not found in [%s]", dateRange),
                        metadata);

                Assert.assertEquals("Wrong NetCDFMetadataBean id", "downloads/gbr4_v2/FAKE_gbr4_v2_29_2010-09-02_10h00_nc", metadata.getId());

            } else if (dateRange.getStartDate().equals(new DateTime(2010, 9, 2, 11, 0, 0, 0, timezone))) {
                frameTimetableFound++;
                // Empty

                Assert.assertEquals(String.format("Wrong end date for [%s]", dateRange),
                        new DateTime(2010, 9, 2, 12, 0, 0, 0, timezone), dateRange.getEndDate());

                Assert.assertEquals(String.format("Wrong number of NetCDFMetadataSet for [%s]", dateRange),
                        0, frameTimetable.size());

            // ...

            } else if (dateRange.getStartDate().equals(new DateTime(2010, 9, 5, 23, 0, 0, 0, timezone))) {
                frameTimetableFound++;
                // Empty

                Assert.assertEquals(String.format("Wrong end date for [%s]", dateRange),
                        new DateTime(2010, 9, 6, 0, 0, 0, 0, timezone), dateRange.getEndDate());

                Assert.assertEquals(String.format("Wrong number of NetCDFMetadataSet for [%s]", dateRange),
                        0, frameTimetable.size());
            }
        }

        Assert.assertEquals("Wrong number of frame timetables", 120, timetableMap.size());
        Assert.assertEquals("Some frame timetables are missing", 8, frameTimetableFound);
    }
}
