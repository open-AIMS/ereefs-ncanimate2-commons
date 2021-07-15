/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.timetable;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Key: layerId
 */
public class FrameTimetable extends HashMap<String, NetCDFMetadataSet> {

    public void add(String layerId, NetCDFMetadataFrame netCDFMetadataFrame) {
        NetCDFMetadataSet metadataSet = this.get(layerId);
        if (metadataSet == null) {
            metadataSet = new NetCDFMetadataSet();
            this.put(layerId, metadataSet);
        }

        metadataSet.add(netCDFMetadataFrame);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        for (Map.Entry<String, NetCDFMetadataSet> entry : this.entrySet()) {
            json.put(entry.getKey(), entry.getValue().toJSON());
        }

        return json;
    }

    @Override
    public String toString() {
        return this.toJSON().toString(4);
    }

}
