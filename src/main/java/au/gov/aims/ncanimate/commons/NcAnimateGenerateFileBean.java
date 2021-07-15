/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons;

import au.gov.aims.ereefs.bean.metadata.netcdf.NetCDFMetadataBean;
import au.gov.aims.ereefs.bean.ncanimate.render.AbstractNcAnimateRenderFileBean;
import au.gov.aims.ncanimate.commons.timetable.DateTimeRange;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.json.JSONObject;

import java.net.URI;
import java.util.Map;

public class NcAnimateGenerateFileBean {
    private static final Logger LOGGER = Logger.getLogger(NcAnimateGenerateFileBean.class);

    private String id;
    private String definitionId; // ncAnimateConfigId
    private String datasetId;
    private String fileId;
    private String previewFileUri; // Used with videos

    // Used when creating the map of files which needs to be generated.
    private Map<String, AbstractNcAnimateRenderFileBean> renderFiles;

    private long lastModified; // last modified timestamp of the generated file(s)

    // Date range representing the output files.
    // For example, a monthly video would have something like:
    //   "2010-09-01T00:00:00.000+10:00" - "2010-10-01T00:00:00.000+10:00"
    private DateTimeRange dateRange;

    public NcAnimateGenerateFileBean(
            String ncAnimateConfigId,
            URI directoryUri,
            String fileId,
            Map<String, AbstractNcAnimateRenderFileBean> renderFiles,
            DateTimeRange dateRange) {

        this.definitionId = ncAnimateConfigId;
        this.fileId = fileId;
        this.datasetId = fileId + "_${ctx.region.id}_${ctx.targetHeight}";

        this.dateRange = dateRange;

        this.renderFiles = renderFiles;

        this.lastModified = 0;

        this.generateFileURIs(directoryUri);
    }

    private void generateFileURIs(URI directoryUri) {
        if (renderFiles != null && !renderFiles.isEmpty()) {

            // Add the directory as specified in config
            String directoryUriStr = directoryUri.toString();
            StringBuilder fileURIPath = new StringBuilder(directoryUriStr);
            if (!directoryUriStr.endsWith("/")) {
                fileURIPath.append("/");
            }

            // Construct the filename without extension
            fileURIPath.append(this.datasetId);
            String fileURIPathStr = fileURIPath.toString();

            for (AbstractNcAnimateRenderFileBean renderFile : renderFiles.values()) {
                // Add the filename extension
                String fileExtension = renderFile.getFileExtension();
                if (fileExtension == null) {
                    throw new IllegalArgumentException(
                            String.format("Invalid ncAnimate config. Missing file extension for render file: %s", renderFile.toString()));
                }

                renderFile.setFileURI(fileURIPathStr + "." + fileExtension);
            }

            this.previewFileUri = fileURIPathStr + "_preview";
        }
    }

    public String getId() {
        if (this.id == null) {
            this.id = NetCDFMetadataBean.getUniqueDatasetId(this.definitionId, this.datasetId, false);
        }

        return this.id;
    }

    public String getNcAnimateConfigId() {
        return this.definitionId;
    }
    public String getDefinitionId() {
        return this.definitionId;
    }

    public String getFileId() {
        return this.fileId;
    }
    public String getDatasetId() {
        return this.datasetId;
    }

    public String getPreviewFileUri(String frameFileExtension) {
        return this.previewFileUri + "." + frameFileExtension;
    }

    public Map<String, AbstractNcAnimateRenderFileBean> getRenderFiles() {
        return this.renderFiles;
    }

    public long getLastModified() {
        return this.lastModified;
    }

    public DateTimeRange getDateRange() {
        return this.dateRange;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();

        json.put("_id", this.getId());
        json.put("definitionId", this.definitionId);
        json.put("datasetId", this.datasetId);
        json.put("fileId", this.fileId);

        if (this.renderFiles != null) {
            JSONObject jsonRenderFiles = new JSONObject();
            for (Map.Entry<String, AbstractNcAnimateRenderFileBean> renderFileEntry : this.renderFiles.entrySet()) {
                String renderFileId = renderFileEntry.getKey();
                AbstractNcAnimateRenderFileBean renderFile = renderFileEntry.getValue();

                if (renderFile != null) {
                    jsonRenderFiles.put(renderFileId, renderFile.toJSON());
                }
            }
            json.put("renderFiles", jsonRenderFiles);
        }

        json.put("lastModified", new DateTime(this.lastModified).toString());
        if (this.dateRange != null) {
            json.put("dateRange", this.dateRange.toJSON());
        }

        return json;
    }

    @Override
    public String toString() {
        return this.toJSON().toString(4);
    }
}
