/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.timetable;

import au.gov.aims.ereefs.bean.metadata.TimeIncrement;
import au.gov.aims.ereefs.bean.metadata.TimeIncrementUnit;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Holds a start date and a end date.
 */
public class DateTimeRange implements Comparable<DateTimeRange> {
    // The whole time history, from the beginning of time to the end of time...
    public static final DateTimeRange ALL_TIME = new DateTimeRange();

    private DateTime startDate;
    private DateTime endDate;

    /**
     * Create a DateTimeRange with a length of the frameTimeIncrement parameter, which includes the randomDateInRange date.
     * NOTE: If frameTimeIncrement is null or its unit is ETERNITY, the DateTimeRange dates will be ALL_TIME.
     *
     * @param randomDateInRange
     * @param frameTimeIncrement
     * @return
     */
    public static DateTimeRange getDateTimeRange(DateTime randomDateInRange, TimeIncrement frameTimeIncrement) {
        if (randomDateInRange == null) {
            return null;
        }

        if (frameTimeIncrement == null) {
            return ALL_TIME;
        }

        TimeIncrementUnit unit = frameTimeIncrement.getUnit();
        if (unit == null || TimeIncrementUnit.ETERNITY.equals(unit)) {
            return ALL_TIME;
        }

        Period period = frameTimeIncrement.getPeriod();
        if (period == null) {
            return ALL_TIME;
        }

        return new DateTimeRange(
                randomDateInRange,
                randomDateInRange.plus(period));

    }

    public static DateTimeRange create(DateTime startDate, DateTime endDate) {
        if (startDate == null && endDate == null) {
            return DateTimeRange.ALL_TIME;
        }

        return new DateTimeRange(startDate, endDate);
    }

    public static DateTimeRange create(JSONObject json, DateTimeZone timezone) {
        if (json == null) {
            return null;
        }

        String startDateStr = json.optString("startDate", null);
        String endDateStr = json.optString("endDate", null);

        DateTime startDate = null;
        if (startDateStr != null) {
            startDate = DateTime.parse(startDateStr).withZone(timezone);
        }

        DateTime endDate = null;
        if (endDateStr != null) {
            endDate = DateTime.parse(endDateStr).withZone(timezone);
        }

        return DateTimeRange.create(startDate, endDate);
    }

    private DateTimeRange(DateTime startDate, DateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    private DateTimeRange() {
        this.startDate = null;
        this.endDate = null;
    }

    public DateTime getStartDate() {
        return this.startDate;
    }

    public DateTime getEndDate() {
        return this.endDate;
    }

    public DateTimeRange next(TimeIncrement frameTimeIncrement) {
        if (this == ALL_TIME || this.endDate == null) {
            return ALL_TIME;
        }

        Period period = frameTimeIncrement.getPeriod();
        if (period == null) {
            return ALL_TIME;
        }

        return new DateTimeRange(
                this.endDate,
                this.endDate.plus(period)
        );
    }

    public static SortedSet<DateTimeRange> mergeDateRanges(Collection<DateTimeRange> dateRangesToMerge) {
        List<DateTimeRange> unmergedDateRanges = new ArrayList<DateTimeRange>(dateRangesToMerge);
        SortedSet<DateTimeRange> mergedDateRanges = new TreeSet<DateTimeRange>();

        while (!unmergedDateRanges.isEmpty()) {
            DateTimeRange mergedDateRange = unmergedDateRanges.remove(0);

            boolean candidateFound;
            do {
                candidateFound = false;
                for (int i=0; i<unmergedDateRanges.size(); i++) {
                    DateTimeRange candidateDateRange = unmergedDateRanges.get(i);
                    if (mergedDateRange.overlapsWith(candidateDateRange)) {

                        // Merge
                        mergedDateRange = mergedDateRange.mergeWith(candidateDateRange);
                        candidateFound = true;

                        // Remove the merged element
                        unmergedDateRanges.remove(i);
                        i--;
                    }
                }
            } while(candidateFound);

            mergedDateRanges.add(mergedDateRange);
        }

        return mergedDateRanges;
    }

    public boolean overlapsWith(DateTimeRange otherDateRange) {
        if (otherDateRange == null) {
            return false;
        }

        if (DateTimeRange.ALL_TIME.equals(this) || DateTimeRange.ALL_TIME.equals(otherDateRange)) {
            return true;
        }

        // This start date is in other date range
        if (otherDateRange.contains(this.getStartDate())) {
            return true;
        }
        // This end date is in other date range
        if (otherDateRange.contains(this.getEndDate())) {
            return true;
        }

        // Other start date is in this date range
        if (this.contains(otherDateRange.getStartDate())) {
            return true;
        }
        // Other end date is in this date range
        if (this.contains(otherDateRange.getEndDate())) {
            return true;
        }

        return false;
    }

    public boolean contains(DateTime date) {
        if (date == null) {
            return false;
        }

        if (DateTimeRange.ALL_TIME.equals(this)) {
            return true;
        }

        return this.getStartDate().compareTo(date) <= 0 &&
                this.getEndDate().compareTo(date) >= 0;
    }

    public boolean contains(DateTimeRange dateRange) {
        if (dateRange == null) {
            return false;
        }

        if (DateTimeRange.ALL_TIME.equals(this)) {
            return true;
        }

        return this.contains(dateRange.getStartDate()) &&
                this.contains(dateRange.getEndDate());
    }

    public DateTimeRange mergeWith(DateTimeRange otherDateRange) {
        if (otherDateRange == null) {
            return this;
        }

        if (DateTimeRange.ALL_TIME.equals(this)) {
            return DateTimeRange.ALL_TIME;
        }

        DateTime thisStartDate = this.getStartDate(),
            otherStartDate = otherDateRange.getStartDate(),
            minStartDate = null;
        if (thisStartDate == null) {
            minStartDate = otherStartDate;
        } else if (otherStartDate == null) {
            minStartDate = thisStartDate;
        } else {
            minStartDate = thisStartDate.compareTo(otherStartDate) < 0 ? thisStartDate : otherStartDate;
        }

        DateTime thisEndDate = this.getEndDate(),
            otherEndDate = otherDateRange.getEndDate(),
            maxEndDate = null;
        if (thisEndDate == null) {
            maxEndDate = otherEndDate;
        } else if (otherEndDate == null) {
            maxEndDate = thisEndDate;
        } else {
            maxEndDate = thisEndDate.compareTo(otherEndDate) > 0 ? thisEndDate : otherEndDate;
        }

        return new DateTimeRange(minStartDate, maxEndDate);
    }

    @Override
    public int compareTo(DateTimeRange o) {
        if (this == o) {
            return 0;
        }

        if (o == null) {
            return -1;
        }

        if (DateTimeRange.ALL_TIME.equals(this)) {
            return -1;
        }

        if (DateTimeRange.ALL_TIME.equals(o)) {
            return 1;
        }

        int startDateCmp = dateCompare(this.startDate, o.startDate);
        return startDateCmp == 0 ? dateCompare(this.endDate, o.endDate) : startDateCmp;
    }
    private static int dateCompare(DateTime date1, DateTime date2) {
        // Both null or same instance
        if (date1 == date2) {
            return 0;
        }

        // Put null at the end
        if (date1 == null) {
            return 1;
        }
        if (date2 == null) {
            return -1;
        }

        return date1.compareTo(date2);
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof DateTimeRange)) {
            return false;
        }

        DateTimeRange other = (DateTimeRange)obj;

        if (DateTimeRange.ALL_TIME.equals(this) || DateTimeRange.ALL_TIME.equals(other)) {
            return false;
        }

        return dateEquals(this.startDate, other.startDate) &&
                dateEquals(this.endDate, other.endDate);
    }
    private static boolean dateEquals(DateTime date1, DateTime date2) {
        // Both null or same instance
        if (date1 == date2) {
            return true;
        }

        // Put null at the end
        if (date1 == null || date2 == null) {
            return false;
        }

        return date1.equals(date2);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        json.put("startDate", this.startDate);
        json.put("endDate", this.endDate);

        return json.isEmpty() ? null : json;
    }

    @Override
    public String toString() {
        if (DateTimeRange.ALL_TIME.equals(this)) {
            return "ALL_TIME";
        }

        return new StringBuilder()
            .append(this.startDate == null ? "null" : this.startDate.toString())
            .append(" - ")
            .append(this.endDate == null ? "null" : this.endDate.toString())
            .toString();
    }
}
