/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons.generator.context;

import au.gov.aims.ereefs.bean.metadata.netcdf.NetCDFMetadataBean;
import au.gov.aims.ncanimate.commons.timetable.NetCDFMetadataFrame;
import org.json.JSONObject;

public class LayerContext {
    private String layerId;
    private NetCDFMetadataFrame netCDFMetadataFrame;
    private Double targetHeight;

    public LayerContext(String layerId, NetCDFMetadataFrame netCDFMetadataFrame, Double targetHeight) {
        this.layerId = layerId;
        this.netCDFMetadataFrame = netCDFMetadataFrame;
        this.targetHeight = targetHeight;
    }

    public String getLayerId() {
        return this.layerId;
    }

    public NetCDFMetadataFrame getNetCDFMetadataFrame() {
        return this.netCDFMetadataFrame;
    }

    public Double getTargetHeight() {
        return this.targetHeight;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject()
            .put("layerId", this.layerId)
            .put("targetHeight", this.targetHeight);

        if (this.netCDFMetadataFrame != null) {
            json.put("frameDate", this.netCDFMetadataFrame.getFrameDateTime());

            NetCDFMetadataBean metadata = this.netCDFMetadataFrame.getMetadata();
            if (metadata != null) {
                json.put("inputFile", metadata.getId());
                json.put("inputFileMetadata", metadata.toJSON());
            }
        }

        return json;
    }

    @Override
    public String toString() {
        return this.toJSON().toString(4);
    }
}
