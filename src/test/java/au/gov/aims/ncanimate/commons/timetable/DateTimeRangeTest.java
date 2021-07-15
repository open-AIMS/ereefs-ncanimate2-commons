/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.timetable;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class DateTimeRangeTest {

    @Test
    public void testContains() {
        int y = 2011, m = 1, d = 1;

        //   0  1  2  3  4  5  6
        //   [-----]
        //   [-----------]
        //         [-----]
        //         [-----------]
        //      [-----]
        DateTimeRange date0_2 = DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  2,0));
        DateTimeRange date0_4 = DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  4,0));
        DateTimeRange date2_4 = DateTimeRange.create(new DateTime(y,m,d,  2,0), new DateTime(y,m,d,  4,0));
        DateTimeRange date2_6 = DateTimeRange.create(new DateTime(y,m,d,  2,0), new DateTime(y,m,d,  6,0));
        DateTimeRange date1_3 = DateTimeRange.create(new DateTime(y,m,d,  1,0), new DateTime(y,m,d,  3,0));

        Assert.assertFalse("0:00-2:00 contains null", date0_2.contains((DateTimeRange)null));

        Assert.assertTrue("0:00-2:00 doesn't contains 0:00-2:00", date0_2.contains(date0_2));
        Assert.assertFalse("0:00-2:00 contains 0:00-4:00", date0_2.contains(date0_4));
        Assert.assertFalse("0:00-2:00 contains 2:00-4:00", date0_2.contains(date2_4));
        Assert.assertFalse("0:00-2:00 contains 2:00-6:00", date0_2.contains(date2_6));
        Assert.assertFalse("0:00-2:00 contains 1:00-3:00", date0_2.contains(date1_3));

        Assert.assertTrue("0:00-4:00 doesn't contains 0:00-2:00", date0_4.contains(date0_2));
        Assert.assertTrue("0:00-4:00 doesn't contains 0:00-4:00", date0_4.contains(date0_4));
        Assert.assertTrue("0:00-4:00 doesn't contains 2:00-4:00", date0_4.contains(date2_4));
        Assert.assertFalse("0:00-4:00 contains 2:00-6:00", date0_4.contains(date2_6));
        Assert.assertTrue("0:00-4:00 doesn't contains 1:00-3:00", date0_4.contains(date1_3));

        Assert.assertFalse("2:00-6:00 contains 0:00-2:00", date2_6.contains(date0_2));
        Assert.assertFalse("2:00-6:00 contains 0:00-4:00", date2_6.contains(date0_4));
        Assert.assertTrue("2:00-6:00 doesn't contains 2:00-4:00", date2_6.contains(date2_4));
        Assert.assertTrue("2:00-6:00 doesn't contains 2:00-6:00", date2_6.contains(date2_6));
        Assert.assertFalse("2:00-6:00 contains 1:00-3:00", date2_6.contains(date1_3));

        Assert.assertFalse("1:00-3:00 contains 0:00-2:00", date1_3.contains(date0_2));
        Assert.assertFalse("1:00-3:00 contains 0:00-4:00", date1_3.contains(date0_4));
        Assert.assertFalse("1:00-3:00 contains 2:00-4:00", date1_3.contains(date2_4));
        Assert.assertFalse("1:00-3:00 contains 2:00-6:00", date1_3.contains(date2_6));
        Assert.assertTrue("1:00-3:00 doesn't contains 1:00-3:00", date1_3.contains(date1_3));
    }

    @Test
    public void testMergeDateRanges() {
        int y = 2011, m = 1, d = 1;
        List<DateTimeRange> unmergedDateRange = new ArrayList<DateTimeRange>();

        //   0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
        //   [-----|-----|-----]        [-----]       [---]
        unmergedDateRange.add(DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  2,0)));
        unmergedDateRange.add(DateTimeRange.create(new DateTime(y,m,d,  4,0), new DateTime(y,m,d,  6,0)));
        unmergedDateRange.add(DateTimeRange.create(new DateTime(y,m,d,  2,0), new DateTime(y,m,d,  4,0)));
        unmergedDateRange.add(DateTimeRange.create(new DateTime(y,m,d,  9,0), new DateTime(y,m,d, 11,0)));
        unmergedDateRange.add(DateTimeRange.create(new DateTime(y,m,d, 14,0), new DateTime(y,m,d, 15,0)));

        // Add overlapping dates
        //   0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
        //      [-----]     [-----]  [--] [---------]
        unmergedDateRange.add(DateTimeRange.create(new DateTime(y,m,d,  1,0), new DateTime(y,m,d,  3,0)));
        unmergedDateRange.add(DateTimeRange.create(new DateTime(y,m,d,  5,0), new DateTime(y,m,d,  7,0)));
        unmergedDateRange.add(DateTimeRange.create(new DateTime(y,m,d,  8,0), new DateTime(y,m,d,  9,0)));
        unmergedDateRange.add(DateTimeRange.create(new DateTime(y,m,d, 10,0), new DateTime(y,m,d, 13,0)));

        //   0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15
        //   [-----|-----|-----]        [-----]       [---]
        // +    [-----]     [-----]  [--] [---------]
        //   ==============================================
        //   [--------------------]  [--------------] [---]
        Set<DateTimeRange> expectedMergedDateRanges = new TreeSet<DateTimeRange>();
        expectedMergedDateRanges.add(DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  7,0)));
        expectedMergedDateRanges.add(DateTimeRange.create(new DateTime(y,m,d,  8,0), new DateTime(y,m,d, 13,0)));
        expectedMergedDateRanges.add(DateTimeRange.create(new DateTime(y,m,d, 14,0), new DateTime(y,m,d, 15,0)));

        SortedSet<DateTimeRange> mergedDateRanges = DateTimeRange.mergeDateRanges(unmergedDateRange);

        Assert.assertNotNull("Merged date ranges is null", mergedDateRanges);

        Set<DateTimeRange> sortedMergedDateRanges = new TreeSet<DateTimeRange>(mergedDateRanges);
        Assert.assertEquals("Unexpected date range found", expectedMergedDateRanges, sortedMergedDateRanges);
    }

    @Test
    public void testOverlapWith() {
        int y = 2011, m = 1, d = 1;

        DateTimeRange date0_2 = DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  2,0));
        DateTimeRange date2_4 = DateTimeRange.create(new DateTime(y,m,d,  2,0), new DateTime(y,m,d,  4,0));
        DateTimeRange date4_6 = DateTimeRange.create(new DateTime(y,m,d,  4,0), new DateTime(y,m,d,  6,0));

        DateTimeRange date0_4 = DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  4,0));
        DateTimeRange date1_3 = DateTimeRange.create(new DateTime(y,m,d,  1,0), new DateTime(y,m,d,  3,0));

        Assert.assertFalse("0:00-2:00 overlaps with null", date0_2.overlapsWith(null));

        Assert.assertTrue("0:00-2:00 doesn't overlaps with 0:00-2:00", date0_2.overlapsWith(date0_2));
        Assert.assertTrue("0:00-2:00 doesn't overlaps with 2:00-4:00", date0_2.overlapsWith(date2_4));
        Assert.assertFalse("0:00-2:00 overlaps with 4:00-6:00", date0_2.overlapsWith(date4_6));
        Assert.assertTrue("0:00-2:00 doesn't overlaps with 1:00-3:00", date0_2.overlapsWith(date1_3));

        Assert.assertTrue("2:00-4:00 doesn't overlaps with 0:00-2:00", date2_4.overlapsWith(date0_2));
        Assert.assertTrue("2:00-4:00 doesn't overlaps with 2:00-4:00", date2_4.overlapsWith(date2_4));
        Assert.assertTrue("2:00-4:00 doesn't overlaps with 4:00-6:00", date2_4.overlapsWith(date4_6));
        Assert.assertTrue("2:00-4:00 doesn't overlaps with 1:00-3:00", date2_4.overlapsWith(date1_3));

        Assert.assertFalse("4:00-6:00 overlaps with 0:00-2:00", date4_6.overlapsWith(date0_2));
        Assert.assertTrue("4:00-6:00 doesn't overlaps with 2:00-4:00", date4_6.overlapsWith(date2_4));
        Assert.assertTrue("4:00-6:00 doesn't overlaps with 4:00-6:00", date4_6.overlapsWith(date4_6));
        Assert.assertFalse("4:00-6:00 overlaps with 1:00-3:00", date4_6.overlapsWith(date1_3));

        Assert.assertTrue("1:00-3:00 doesn't overlaps with 0:00-2:00", date1_3.overlapsWith(date0_2));
        Assert.assertTrue("1:00-3:00 doesn't overlaps with 2:00-4:00", date1_3.overlapsWith(date2_4));
        Assert.assertFalse("1:00-3:00 overlaps with 4:00-6:00", date1_3.overlapsWith(date4_6));
        Assert.assertTrue("1:00-3:00 doesn't overlaps with 1:00-3:00", date1_3.overlapsWith(date1_3));

        Assert.assertTrue("0:00-4:00 doesn't overlaps with 1:00-3:00", date0_4.overlapsWith(date1_3));
        Assert.assertTrue("1:00-3:00 doesn't overlaps with 0:00-4:00", date1_3.overlapsWith(date0_4));
    }

    @Test
    public void testMergeWith() {
        int y = 2011, m = 1, d = 1;

        DateTimeRange date0_2 = DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  2,0));
        DateTimeRange date2_4 = DateTimeRange.create(new DateTime(y,m,d,  2,0), new DateTime(y,m,d,  4,0));
        DateTimeRange date4_6 = DateTimeRange.create(new DateTime(y,m,d,  4,0), new DateTime(y,m,d,  6,0));

        DateTimeRange date1_3 = DateTimeRange.create(new DateTime(y,m,d,  1,0), new DateTime(y,m,d,  3,0));

        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  2,0)), date0_2.mergeWith(null));

        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  2,0)), date0_2.mergeWith(date0_2));
        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  4,0)), date0_2.mergeWith(date2_4));
        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  3,0)), date0_2.mergeWith(date1_3));

        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  4,0)), date2_4.mergeWith(date0_2));
        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  2,0), new DateTime(y,m,d,  4,0)), date2_4.mergeWith(date2_4));
        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  2,0), new DateTime(y,m,d,  6,0)), date2_4.mergeWith(date4_6));
        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  1,0), new DateTime(y,m,d,  4,0)), date2_4.mergeWith(date1_3));

        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  2,0), new DateTime(y,m,d,  6,0)), date4_6.mergeWith(date2_4));
        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  4,0), new DateTime(y,m,d,  6,0)), date4_6.mergeWith(date4_6));

        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  0,0), new DateTime(y,m,d,  3,0)), date1_3.mergeWith(date0_2));
        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  1,0), new DateTime(y,m,d,  4,0)), date1_3.mergeWith(date2_4));
        Assert.assertEquals(DateTimeRange.create(new DateTime(y,m,d,  1,0), new DateTime(y,m,d,  3,0)), date1_3.mergeWith(date1_3));
    }
}
