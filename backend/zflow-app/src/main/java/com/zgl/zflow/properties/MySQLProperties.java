package com.zgl.zflow.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datasource.mysql", ignoreInvalidFields = true)
public class MySQLProperties extends SQLDataSourceProperties {}
