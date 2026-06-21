package com.experimentms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class ExternalDataSourceConfig {
    @Value("${app.external.host:localhost}")
    private String host;

    @Value("${app.external.port:3306}")
    private int port;

    @Value("${app.external.username:root}")
    private String username;

    @Value("${app.external.password:123456}")
    private String password;

    @Value("${app.external.user-db:cloud_user_db}")
    private String userDb;

    @Value("${app.external.device-db:cloud_device_db}")
    private String deviceDb;

    @Value("${app.external.sensor-data-db:cloud_sensor_data_db}")
    private String sensorDataDb;

    @Bean
    @Primary
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean("userJdbc")
    public NamedParameterJdbcTemplate userJdbc() {
        return new NamedParameterJdbcTemplate(dataSource(userDb));
    }

    @Bean("deviceJdbc")
    public NamedParameterJdbcTemplate deviceJdbc() {
        return new NamedParameterJdbcTemplate(dataSource(deviceDb));
    }

    @Bean("sensorDataJdbc")
    public NamedParameterJdbcTemplate sensorDataJdbc() {
        return new NamedParameterJdbcTemplate(dataSource(sensorDataDb));
    }

    private DataSource dataSource(String database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false");
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}
