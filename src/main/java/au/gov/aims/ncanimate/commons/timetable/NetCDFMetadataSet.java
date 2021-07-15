/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.timetable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.TreeSet;

public class NetCDFMetadataSet extends TreeSet<NetCDFMetadataFrame> {

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        JSONArray jsonSet = new JSONArray();
        for (NetCDFMetadataFrame metadataFrame : this) {
            if (metadataFrame != null) {
                jsonSet.put(metadataFrame.toJSON());
            }
        }
        json.put("metadataSet", jsonSet);

        return json;
    }

    @Override
    public String toString() {
        return this.toJSON().toString(4);
    }
}
