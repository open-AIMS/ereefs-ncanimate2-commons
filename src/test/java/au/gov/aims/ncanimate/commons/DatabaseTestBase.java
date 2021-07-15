/*
 * Copyright (c) Australian Institute of Marine Science, 2021.
 * @author Gael Lafond <g.lafond@aims.gov.au>
 */
package au.gov.aims.ncanimate.commons;

import au.gov.aims.ereefs.Utils;
import au.gov.aims.ereefs.bean.metadata.netcdf.NetCDFMetadataBean;
import au.gov.aims.ereefs.database.CacheStrategy;
import au.gov.aims.ereefs.database.DatabaseClient;
import au.gov.aims.ereefs.database.manager.DownloadManager;
import au.gov.aims.ereefs.database.manager.MetadataManager;
import au.gov.aims.ereefs.database.manager.ncanimate.ConfigManager;
import au.gov.aims.ereefs.database.manager.ncanimate.ConfigPartManager;
import au.gov.aims.ereefs.database.table.DatabaseTable;
import au.gov.aims.ereefs.helper.NcAnimateConfigHelper;
import au.gov.aims.ereefs.helper.TestHelper;
import com.mongodb.ServerAddress;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DatabaseTestBase {
    private static final Logger LOGGER = Logger.getLogger(DatabaseTestBase.class);
    private static final String DATABASE_NAME = "testdb";

    private MongoServer server;
    private DatabaseClient databaseClient;

    public DatabaseClient getDatabaseClient() {
        return this.databaseClient;
    }

    @Before
    public void init() throws Exception {
        File dbCacheDir = DatabaseTable.getDatabaseCacheDirectory();
        Utils.deleteDirectory(dbCacheDir);

        this.server = new MongoServer(new MemoryBackend());
        InetSocketAddress serverAddress = this.server.bind();

        this.databaseClient = new DatabaseClient(new ServerAddress(serverAddress), DATABASE_NAME);
        this.createTables();
    }

    @After
    public void shutdown() {
        NcAnimateConfigHelper.clearMetadataCache();
        if (this.server != null) {
            this.server.shutdown();
        }
    }

    private void createTables() throws Exception {
        TestHelper.createTables(this.databaseClient);
    }


    public void populateDatabase() throws Exception {
        this.insertTestDownloads();
        this.insertTestConfigParts();
        this.insertTestConfigs();
    }

    public void insertFakePartialGBR4NetCDFFile() throws Exception {
        URL netCDFFileUrl = DatabaseTestBase.class.getClassLoader().getResource("netcdf/gbr4_v2_2010-09-01_00h00-02h00.nc");
        File netCDFFileOrig = new File(netCDFFileUrl.getFile());

        String definitionId = "downloads/gbr4_v2";
        String datasetId = "gbr4_v2_2010-09-01_00h00-02h00.nc";
        URI fileURI = new File("/tmp/netcdfFiles/gbr4_v2_2010-09-01_00h00-02h00.nc").toURI();

        NetCDFMetadataBean metadata = NetCDFMetadataBean.create(definitionId, datasetId, fileURI, netCDFFileOrig, netCDFFileOrig.lastModified());

        MetadataManager metadataManager = new MetadataManager(this.getDatabaseClient(), CacheStrategy.DISK);
        metadataManager.save(metadata.toJSON());
    }

    /**
     * Creates hourly files containing hourly data (a single time frame per file)
     */
    public void insertFakeHourlyHourlyData(int nbHours) throws Exception {
        DateTimeZone timezone = DateTimeZone.forID("Australia/Brisbane");

        URL netCDFFileUrl = DatabaseTestBase.class.getClassLoader().getResource("netcdf/gbr4_v2_2010-09-01_00h00-02h00.nc");
        File netCDFFileOrig = new File(netCDFFileUrl.getFile());

        String definitionId = "downloads/gbr4_v2";
        String datasetId = "gbr4_v2_2010-09-01_00h00-02h00.nc";
        URI fileURI = new File("/tmp/netcdfFiles/gbr4_v2_2010-09-01_00h00-02h00.nc").toURI();

        NetCDFMetadataBean metadata = NetCDFMetadataBean.create(definitionId, datasetId, fileURI, netCDFFileOrig, netCDFFileOrig.lastModified());
        MetadataManager metadataManager = new MetadataManager(this.getDatabaseClient(), CacheStrategy.DISK);

        // Create a bunch of fake files based on the real one
        for (int i=0; i<nbHours; i++) {
            JSONObject jsonFakeMetadata = metadata.toJSON();

            DateTime startDate = new DateTime(2010, 9, 1, 5, 0, timezone).plusHours(i);
            DateTime endDate = new DateTime(2010, 9, 1, 5, 0, timezone).plusHours(i);

            List<String> fakeTimeValues = new ArrayList<String>();
            fakeTimeValues.add(startDate.toString());
            fakeTimeValues.add(endDate.toString());

            // Alter properties
            String fakeDatasetId = String.format("FAKE_gbr4_v2_%d_%04d-%02d-%02d_%02dh%02d.nc", i, startDate.getYear(), startDate.getMonthOfYear(), startDate.getDayOfMonth(), startDate.getHourOfDay(), startDate.getMinuteOfHour());
            jsonFakeMetadata.put("_id", NetCDFMetadataBean.getUniqueDatasetId(definitionId, fakeDatasetId));
            jsonFakeMetadata.put("datasetId", fakeDatasetId);

            // Change dates on variables
            JSONObject jsonFakeVariables = jsonFakeMetadata.optJSONObject("variables");
            for (String fakeVariableId : jsonFakeVariables.keySet()) {
                JSONObject jsonFakeVariable = jsonFakeVariables.optJSONObject(fakeVariableId);
                JSONObject jsonFakeTemporalDomain = jsonFakeVariable.optJSONObject("temporalDomain");
                if (jsonFakeTemporalDomain != null) {
                    jsonFakeTemporalDomain.put("minDate", startDate.toString());
                    jsonFakeTemporalDomain.put("maxDate", endDate.toString());
                    jsonFakeTemporalDomain.put("timeValues", fakeTimeValues);
                }
            }

            metadataManager.save(jsonFakeMetadata);
        }
    }

    /**
     * Creates monthly files containing hourly data
     */
    public void insertFakeMonthlyHourlyData(int nbMonths) throws Exception {
        DateTimeZone timezone = DateTimeZone.forID("Australia/Brisbane");
        insertFakeMonthlyHourlyData("downloads/gbr4_v2", "gbr4_v2", new DateTime(2010, 9, 1, 0, 0, timezone), nbMonths);
    }

    public void insertFakeMonthlyHourlyData(String definitionId, String datasetIdPrefix, DateTime dataStartDate, int nbMonths) throws Exception {
        URL netCDFFileUrl = DatabaseTestBase.class.getClassLoader().getResource("netcdf/gbr4_v2_2010-09-01_00h00-02h00.nc");
        File netCDFFileOrig = new File(netCDFFileUrl.getFile());

        String datasetId = "gbr4_v2_2010-09-01_00h00-02h00.nc";
        URI fileURI = new File("/tmp/netcdfFiles/gbr4_v2_2010-09-01_00h00-02h00.nc").toURI();

        NetCDFMetadataBean metadata = NetCDFMetadataBean.create(definitionId, datasetId, fileURI, netCDFFileOrig, netCDFFileOrig.lastModified());
        MetadataManager metadataManager = new MetadataManager(this.getDatabaseClient(), CacheStrategy.DISK);

        // Create a bunch of fake files based on the real one
        for (int i=0; i<nbMonths; i++) {
            JSONObject jsonFakeMetadata = metadata.toJSON();

            DateTime startDate = dataStartDate.plusMonths(i);
            DateTime endDate = dataStartDate.plusMonths(i+1);

            List<String> fakeTimeValues = new ArrayList<String>();
            DateTime fakeDate = startDate;
            while (fakeDate.compareTo(endDate) < 0) {
                fakeTimeValues.add(fakeDate.toString());
                fakeDate = fakeDate.plusHours(1);
            }

            // Alter properties
            String fakeDatasetId = String.format("FAKE_%s_%d_%04d-%02d-%02d.nc",
                    datasetIdPrefix, i, startDate.getYear(), startDate.getMonthOfYear(), startDate.getDayOfMonth());
            jsonFakeMetadata.put("_id", NetCDFMetadataBean.getUniqueDatasetId(definitionId, fakeDatasetId));
            jsonFakeMetadata.put("datasetId", fakeDatasetId);

            // Change dates on variables
            JSONObject jsonFakeVariables = jsonFakeMetadata.optJSONObject("variables");
            for (String fakeVariableId : jsonFakeVariables.keySet()) {
                JSONObject jsonFakeVariable = jsonFakeVariables.optJSONObject(fakeVariableId);
                JSONObject jsonFakeTemporalDomain = jsonFakeVariable.optJSONObject("temporalDomain");
                if (jsonFakeTemporalDomain != null) {
                    jsonFakeTemporalDomain.put("minDate", startDate.toString());
                    jsonFakeTemporalDomain.put("maxDate", endDate.toString());
                    jsonFakeTemporalDomain.put("timeValues", fakeTimeValues);
                }
            }

            metadataManager.save(jsonFakeMetadata);
        }
    }

    /**
     * Creates daily files containing daily data
     */
    public void insertFakeGBR4DailyDailyData(int nbDays) throws Exception {
        this.insertFakeDailyDailyData(nbDays, "downloads/gbr4_v2_daily", "gbr4_v2_daily", "Australia/Brisbane");
    }
    public void insertFakeIMOSDailyDailyData(int nbDays) throws Exception {
        this.insertFakeDailyDailyData(nbDays, "downloads/imos", "imos", "Etc/GMT-6");
    }
    public void insertFakeDailyDailyData(int nbDays, String definitionId, String filePrefix, String timeZone) throws Exception {
        DateTimeZone timezone = DateTimeZone.forID(timeZone);

        URL netCDFFileUrl = DatabaseTestBase.class.getClassLoader().getResource("netcdf/gbr4_v2_2010-09-01_00h00-02h00.nc");
        File netCDFFileOrig = new File(netCDFFileUrl.getFile());

        // Base info, which is overwritten for each file generated, in the "for" loop bellow
        String datasetId = "gbr4_v2_2010-09-01_00h00-02h00.nc";
        URI fileURI = new File("/tmp/netcdfFiles/gbr4_v2_2010-09-01_00h00-02h00.nc").toURI();

        NetCDFMetadataBean metadata = NetCDFMetadataBean.create(definitionId, datasetId, fileURI, netCDFFileOrig, netCDFFileOrig.lastModified());
        MetadataManager metadataManager = new MetadataManager(this.getDatabaseClient(), CacheStrategy.DISK);

        // Create a bunch of fake files based on the real one
        for (int i=0; i<nbDays; i++) {
            JSONObject jsonFakeMetadata = metadata.toJSON();

            DateTime startDate = new DateTime(2010, 9, 1, 0, 0, timezone).plusDays(i);
            DateTime endDate = new DateTime(2010, 9, 1, 0, 0, timezone).plusDays(i);

            List<String> fakeTimeValues = new ArrayList<String>();
            fakeTimeValues.add(startDate.toString());
            fakeTimeValues.add(endDate.toString());

            // Alter properties
            String fakeDatasetId = String.format("FAKE_%s_%d_%04d-%02d-%02d.nc", filePrefix, i, startDate.getYear(), startDate.getMonthOfYear(), startDate.getDayOfMonth());
            jsonFakeMetadata.put("_id", NetCDFMetadataBean.getUniqueDatasetId(definitionId, fakeDatasetId));
            jsonFakeMetadata.put("datasetId", fakeDatasetId);

            // Change dates on variables
            JSONObject jsonFakeVariables = jsonFakeMetadata.optJSONObject("variables");
            for (String fakeVariableId : jsonFakeVariables.keySet()) {
                JSONObject jsonFakeVariable = jsonFakeVariables.optJSONObject(fakeVariableId);
                JSONObject jsonFakeTemporalDomain = jsonFakeVariable.optJSONObject("temporalDomain");
                if (jsonFakeTemporalDomain != null) {
                    jsonFakeTemporalDomain.put("minDate", startDate.toString());
                    jsonFakeTemporalDomain.put("maxDate", endDate.toString());
                    jsonFakeTemporalDomain.put("timeValues", fakeTimeValues);
                }
            }

            metadataManager.save(jsonFakeMetadata);
        }
    }

    private void insertTestDownloads() throws Exception {
        DownloadManager downloadManager = new DownloadManager(this.getDatabaseClient(), CacheStrategy.DISK);

        TestHelper.insertTestConfigs(downloadManager, "download", "download");
    }

    private void insertTestConfigParts() throws Exception {
        ConfigPartManager configPartManager = new ConfigPartManager(this.getDatabaseClient(), CacheStrategy.DISK);

        TestHelper.insertTestConfigs(configPartManager, "ncanimate/configParts/canvas",    "canvas");
        TestHelper.insertTestConfigs(configPartManager, "ncanimate/configParts/input",     "input");
        TestHelper.insertTestConfigs(configPartManager, "ncanimate/configParts/layers",    "layer");
        TestHelper.insertTestConfigs(configPartManager, "ncanimate/configParts/legends",   "legend");
        TestHelper.insertTestConfigs(configPartManager, "ncanimate/configParts/panels",    "panel");
        TestHelper.insertTestConfigs(configPartManager, "ncanimate/configParts/regions",   "region");
        TestHelper.insertTestConfigs(configPartManager, "ncanimate/configParts/variables", "variable");
    }

    private void insertTestConfigs() throws Exception {
        ConfigManager configManager = new ConfigManager(this.getDatabaseClient(), CacheStrategy.DISK);

        TestHelper.insertTestConfigs(configManager, "ncanimate", "NcAnimate configuration");
    }
}
