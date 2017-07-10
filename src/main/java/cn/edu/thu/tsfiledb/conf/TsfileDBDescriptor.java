package cn.edu.thu.tsfiledb.conf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.edu.thu.tsfile.common.constant.SystemConstant;

public class TsfileDBDescriptor {
	private static final Logger LOGGER = LoggerFactory.getLogger(TsfileDBDescriptor.class);

	private static TsfileDBDescriptor descriptor = new TsfileDBDescriptor();

	private TsfileDBDescriptor() {
		loadProps();
	}

	public static TsfileDBDescriptor getInstance() {
		return descriptor;
	}

	public TsfileDBConfig getConfig() {
		return conf;
	}

	private TsfileDBConfig conf = new TsfileDBConfig();

	/**
	 * load an properties file and set TsfileDBConfig variables
	 *
	 */
	private void loadProps() {
		String tsfileHome = System.getProperty(SystemConstant.TSFILE_HOME, TsfileDBConfig.CONFIG_DEFAULT_PATH);
		String url;
		InputStream inputStream = null;
		if (tsfileHome.equals(TsfileDBConfig.CONFIG_DEFAULT_PATH)) {
			url = tsfileHome;
			try {
			    inputStream = new FileInputStream(new File(url));
			} catch (Exception e) {
			    LOGGER.error("Fail to find config file {}", url, e);
			    return;
			}
			
		} else {
			url = tsfileHome + "/conf/"+TsfileDBConfig.CONFIG_NAME;
			try {
				File file = new File(url);
				inputStream = new FileInputStream(file);
			} catch (FileNotFoundException e) {
				LOGGER.error("Fail to find config file {}", url, e);
				System.exit(1);
			}
		}
		LOGGER.info("Start to read config file {}", url);
		Properties properties = new Properties();
		try {
			properties.load(inputStream);
			conf.rpcPort = Integer.parseInt(properties.getProperty("rpc_port",conf.rpcPort+""));
			
			conf.enableWal = Boolean.parseBoolean(properties.getProperty("enable_wal", conf.enableWal+""));
			conf.walFolder = properties.getProperty("wal_folder", conf.walFolder);
			conf.walCleanupThreshold = Integer.parseInt(properties.getProperty("wal_cleanup_threshold", conf.walCleanupThreshold+""));
			conf.flushWalThreshold = Integer.parseInt(properties.getProperty("flush_wal_threshold", conf.flushWalThreshold+""));
			conf.flushWalPeriodInMs = Integer.parseInt(properties.getProperty("flush_wal_period_in_ms", conf.flushWalPeriodInMs+""));
			
			conf.overflowDataDir = properties.getProperty("overflow_data_dir", conf.overflowDataDir);
			conf.fileNodeDir = properties.getProperty("file_node_dir", conf.fileNodeDir);
			conf.bufferWriteDir = properties.getProperty("buffer_write_dir", conf.bufferWriteDir);
			conf.metadataDir = properties.getProperty("metadata_dir", conf.metadataDir);
			conf.derbyHome = properties.getProperty("derby_dir", conf.derbyHome);
			
			conf.mergeConcurrentThreads = Integer.parseInt(properties.getProperty("merge_concurrent_threads", conf.mergeConcurrentThreads + ""));
			conf.maxOpenFolder = Integer.parseInt(properties.getProperty("max_opened_folder", conf.maxOpenFolder + ""));
			
			conf.fetchSize = Integer.parseInt(properties.getProperty("fetch_size", conf.fetchSize + ""));
		} catch (IOException e) {
			LOGGER.warn("Cannot load config file, use default configuration", e);
		} catch (Exception e) {
			LOGGER.warn("Error format in config file, use default configuration", e);
		}
		if (inputStream != null) {
			try {
				inputStream.close();
			} catch (IOException e) {
				LOGGER.error("Fail to close config file input stream", e);
			}
		}
	}
}