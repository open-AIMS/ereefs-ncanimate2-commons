/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.timetable;

import au.gov.aims.ereefs.bean.metadata.netcdf.NetCDFMetadataBean;
import au.gov.aims.ereefs.bean.metadata.netcdf.TemporalDomainBean;
import au.gov.aims.ereefs.bean.metadata.netcdf.VariableMetadataBean;
import org.joda.time.DateTime;
import org.json.JSONObject;

import java.util.Map;

public class NetCDFMetadataFrame implements Comparable<NetCDFMetadataFrame> {
    private DateTime frameDateTime;
    private NetCDFMetadataBean metadata;
    private VariableMetadataBean mostSignificantVariableMetadata;

    public NetCDFMetadataFrame(DateTime frameDateTime, NetCDFMetadataBean metadata, String mostSignificantVariableId) {
        this.frameDateTime = frameDateTime;
        this.metadata = metadata;

        if (this.metadata != null && mostSignificantVariableId != null && !mostSignificantVariableId.isEmpty()) {
            Map<String, VariableMetadataBean> variableMetadataMap = this.metadata.getVariableMetadataBeanMap();
            if (variableMetadataMap != null && !variableMetadataMap.isEmpty()) {
                this. mostSignificantVariableMetadata = variableMetadataMap.get(mostSignificantVariableId);
            }
        }
    }

    public NetCDFMetadataFrame(DateTime frameDateTime, NetCDFMetadataBean metadata, VariableMetadataBean mostSignificantVariableMetadata) {
        this.frameDateTime = frameDateTime;
        this.metadata = metadata;
        this.mostSignificantVariableMetadata = mostSignificantVariableMetadata;
    }

    public DateTime getFrameDateTime() {
        return this.frameDateTime;
    }

    public NetCDFMetadataBean getMetadata() {
        return this.metadata;
    }

    @Override
    public int compareTo(NetCDFMetadataFrame o) {
        // Same instance or both null
        if (this.metadata == o.metadata) {
            return 0;
        }

        // Move null at the end
        if (this.metadata == null) {
            return 1;
        }
        if (o.metadata == null) {
            return -1;
        }

        TemporalDomainBean tempDomain1 = null;
        VariableMetadataBean variable1 = this.mostSignificantVariableMetadata;
        if (variable1 != null) {
            tempDomain1 = variable1.getTemporalDomainBean();
        }

        TemporalDomainBean tempDomain2 = null;
        VariableMetadataBean variable2 = o.mostSignificantVariableMetadata;
        if (variable2 != null) {
            tempDomain2 = variable2.getTemporalDomainBean();
        }

        // Same instance or both null
        // Can't return 0, they are not the same metadata, they just have the same temporal domain
        if (tempDomain1 == tempDomain2) {
            return 1;
        }

        // Move null at the end
        if (tempDomain1 == null) {
            return 1;
        }
        if (tempDomain2 == null) {
            return -1;
        }

        DateTime maxDate1 = tempDomain1.getMaxDate();
        DateTime maxDate2 = tempDomain2.getMaxDate();

        if (maxDate1 != null && maxDate2 != null) {
            int cmp = maxDate1.compareTo(maxDate2);
            if (cmp < 0) {
                return 1;
            }
            if (cmp > 0) {
                return -1;
            }
        }

        DateTime minDate1 = tempDomain1.getMinDate();
        DateTime minDate2 = tempDomain2.getMinDate();

        if (minDate1 != null && minDate2 != null) {
            int cmp = minDate1.compareTo(minDate2);
            if (cmp < 0) {
                return 1;
            }
            if (cmp > 0) {
                return -1;
            }
        }

        // Same temporal domain or too many null values to do proper comparison;
        // return something different than 0 (they are different but we can't decide which one goes first)
        return 1;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        json.put("frameDateTime", frameDateTime);
        json.put("metadata", metadata.getId());

        return json;
    }

    @Override
    public int hashCode() {
        // The metadata ID is unique, that should be sufficient
        return this.toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof NetCDFMetadataFrame)) {
            return false;
        }

        NetCDFMetadataFrame other = (NetCDFMetadataFrame)obj;

        // The metadata ID is unique, that should be sufficient
        return this.toString().equals(other.toString());
    }

    @Override
    public String toString() {
        return this.toJSON().toString(4);
    }
}
