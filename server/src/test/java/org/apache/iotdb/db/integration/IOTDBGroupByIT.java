/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.integration;

import org.apache.iotdb.db.service.IoTDB;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.jdbc.Config;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.*;

import static org.apache.iotdb.db.integration.Constant.*;
import static org.junit.Assert.fail;

public class IOTDBGroupByIT {

  private static IoTDB daemon;

  private static String[] dataSet1 = new String[]{
      "SET STORAGE GROUP TO root.ln.wf01.wt01",
      "CREATE TIMESERIES root.ln.wf01.wt01.status WITH DATATYPE=BOOLEAN, ENCODING=PLAIN",
      "CREATE TIMESERIES root.ln.wf01.wt01.temperature WITH DATATYPE=DOUBLE, ENCODING=PLAIN",
      "CREATE TIMESERIES root.ln.wf01.wt01.hardware WITH DATATYPE=INT32, ENCODING=PLAIN",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(1, 1.1, false, 11)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(2, 2.2, true, 22)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(3, 3.3, false, 33 )",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(4, 4.4, false, 44)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(5, 5.5, false, 55)",
      "flush",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(100, 100.1, false, 110)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(150, 200.2, true, 220)",
      "flush",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(200, 300.3, false, 330 )",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(250, 400.4, false, 440)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(300, 500.5, false, 550)",
      "flush",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(10, 10.1, false, 110)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(20, 20.2, true, 220)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(30, 30.3, false, 330 )",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(40, 40.4, false, 440)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(50, 50.5, false, 550)",
      "flush",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(500, 100.1, false, 110)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(510, 200.2, true, 220)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(520, 300.3, false, 330 )",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(530, 400.4, false, 440)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(540, 500.5, false, 550)",
      "flush",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(580, 100.1, false, 110)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(590, 200.2, true, 220)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(600, 300.3, false, 330 )",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(610, 400.4, false, 440)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(620, 500.5, false, 550)",
  };

  private static final String TIMESTAMP_STR = "Time";

  @Before
  public void setUp() throws Exception {
    EnvironmentUtils.closeStatMonitor();
    daemon = IoTDB.getInstance();
    daemon.active();
    EnvironmentUtils.envSetUp();
    Class.forName(Config.JDBC_DRIVER_NAME);
    prepareData();
  }

  @After
  public void tearDown() throws Exception {
    daemon.stop();
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void countSumAvgTest() throws SQLException {
    String[] retArray1 = new String[]{
        "5,3,35.8,11.933333333333332",
        "25,2,70.7,35.35",
        "45,1,50.5,50.5",
        "65,0,0.0,null",
        "85,1,100.1,100.1",
        "105,0,0.0,null",
        "125,0,0.0,null",
        "145,1,200.2,200.2"
    };
    String[] retArray2 = new String[]{
        "50,1,50.5,50.5",
        "60,0,0.0,null",
        "70,0,0.0,null",
        "80,0,0.0,null",
        "90,0,0.0,null",
        "100,1,100.1,100.1",
        "110,0,0.0,null",
        "120,0,0.0,null",
        "130,0,0.0,null",
        "140,0,0.0,null",
        "150,1,200.2,200.2",
        "160,0,0.0,null"
    };
    String[] retArray3 = new String[]{
            "25,2,70.7,35.35",
            "45,1,50.5,50.5",
            "65,0,0.0,null",
            "85,1,100.1,100.1",
            "105,0,0.0,null",
            "125,0,0.0,null",
            "145,1,200.2,200.2",
            "165,0,0.0,null",
            "185,1,300.3,300.3",
            "205,0,0.0,null",
            "225,0,0.0,null",
            "245,1,400.4,400.4",
            "265,0,0.0,null",
            "285,1,500.5,500.5",
            "305,0,0.0,null"
    };
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select count(temperature), sum(temperature), avg(temperature) from "
              + "root.ln.wf01.wt01 where time > 3 "
              + "GROUP BY ([5, 160], 20ms)");

      Assert.assertTrue(hasResultSet);
      int cnt;
      try (ResultSet resultSet = statement.getResultSet()) {
        cnt = 0;
        while (resultSet.next()) {
          String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
              .getString(count("root.ln.wf01.wt01.temperature")) + "," +
              resultSet.getString(sum("root.ln.wf01.wt01.temperature")) + "," + resultSet
              .getString(avg("root.ln.wf01.wt01.temperature"));
          Assert.assertEquals(retArray1[cnt], ans);
          cnt++;
        }
        Assert.assertEquals(retArray1.length, cnt);
      }

      hasResultSet = statement.execute(
          "select count(temperature), sum(temperature), avg(temperature) from "
              + "root.ln.wf01.wt01 where temperature > 3 "
              + "GROUP BY ([50, 160], 10ms)");

      Assert.assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        cnt = 0;
        while (resultSet.next()) {
          String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
              .getString(count("root.ln.wf01.wt01.temperature")) + "," +
              resultSet.getString(sum("root.ln.wf01.wt01.temperature")) + "," + resultSet
              .getString(avg("root.ln.wf01.wt01.temperature"));
          Assert.assertEquals(retArray2[cnt], ans);
          cnt++;
        }
        Assert.assertEquals(retArray2.length, cnt);
      }

      hasResultSet = statement.execute(
          "select count(temperature), sum(temperature), avg(temperature) from "
              + "root.ln.wf01.wt01 where temperature > 3 "
              + "GROUP BY ([25, 314], 20ms)");

      Assert.assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        cnt = 0;
        while (resultSet.next()) {
          String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
              .getString(count("root.ln.wf01.wt01.temperature")) + "," +
              resultSet.getString(sum("root.ln.wf01.wt01.temperature")) + "," + resultSet
              .getString(avg("root.ln.wf01.wt01.temperature"));
          Assert.assertEquals(retArray3[cnt], ans);
          cnt++;
        }
        Assert.assertEquals(retArray3.length, cnt);
      }

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void maxMinValeTimeTest() throws SQLException {
    String[] retArray1 = new String[]{
        "2,null,null,null,null",
        "4,5.5,4.4,5,4",
        "6,null,null,null,null",
        "8,null,null,null,null",
        "10,10.1,10.1,10,10",
        "12,null,null,null,null",
        "14,null,null,null,null",
        "16,null,null,null,null",
        "18,null,null,null,null",
        "20,20.2,20.2,20,20",
        "22,null,null,null,null",
        "24,null,null,null,null",
        "26,null,null,null,null",
        "28,null,null,null,null",
        "30,30.3,30.3,30,30"
    };
    String[] retArray2 = new String[]{
        "2,20.2,3.3,20,3",
        "22,40.4,30.3,40,30",
        "42,50.5,50.5,50,50",
        "62,null,null,null,null",
        "82,100.1,100.1,100,100",
        "102,null,null,null,null",
        "122,null,null,null,null",
        "142,200.2,200.2,150,150",
        "162,null,null,null,null",
        "182,300.3,300.3,200,200",
        "202,null,null,null,null",
        "222,null,null,null,null",
        "242,400.4,400.4,250,250",
        "262,null,null,null,null",
        "282,500.5,500.5,300,300",
    };
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {

      boolean hasResultSet = statement.execute(
          "select max_value(temperature), min_value(temperature), max_time(temperature), "
              + "min_time(temperature) from root.ln.wf01.wt01 where time > 3 "
              + "GROUP BY ([2,30], 2ms)");

      Assert.assertTrue(hasResultSet);
      int cnt;
      try (ResultSet resultSet = statement.getResultSet()) {
        cnt = 0;
        while (resultSet.next()) {
          String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
              .getString(max_value("root.ln.wf01.wt01.temperature"))
              + "," + resultSet.getString(min_value("root.ln.wf01.wt01.temperature")) + ","
              + resultSet.getString(max_time("root.ln.wf01.wt01.temperature"))
              + "," + resultSet.getString(min_time("root.ln.wf01.wt01.temperature"));
          Assert.assertEquals(retArray1[cnt], ans);
          cnt++;
        }
        Assert.assertEquals(retArray1.length, cnt);
      }

      hasResultSet = statement.execute(
          "select max_value(temperature), min_value(temperature), max_time(temperature), "
              + "min_time(temperature) from root.ln.wf01.wt01 where temperature > 3 "
              + "GROUP BY ([2,300], 20ms)");

      Assert.assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        cnt = 0;
        while (resultSet.next()) {
          String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
              .getString(max_value("root.ln.wf01.wt01.temperature"))
              + "," + resultSet.getString(min_value("root.ln.wf01.wt01.temperature")) + ","
              + resultSet.getString(max_time("root.ln.wf01.wt01.temperature"))
              + "," + resultSet.getString(min_time("root.ln.wf01.wt01.temperature"));
          Assert.assertEquals(retArray2[cnt], ans);
          cnt++;
        }
      }
      Assert.assertEquals(retArray2.length, cnt);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void firstLastTest() throws SQLException {
    String[] retArray1 = new String[]{
            "2,5.5,4.4",
            "6,null,null",
            "10,10.1,10.1",
            "14,null,null",
            "18,20.2,20.2",
            "22,null,null",
            "26,null,null",
            "30,30.3,30.3"
    };
    String[] retArray2 = new String[]{
            "2,20.2,3.3",
            "22,40.4,30.3",
            "42,50.5,50.5",
            "62,null,null",
            "82,100.1,100.1",
            "102,null,null",
            "122,null,null",
            "142,200.2,200.2",
            "162,null,null",
            "182,300.3,300.3",
            "202,null,null",
            "222,null,null",
            "242,400.4,400.4",
            "262,null,null",
            "282,500.5,500.5"
    };
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select last_value(temperature), first_value(temperature) from root.ln.wf01.wt01 where time > 3 "
              + "GROUP BY ([2,30], 4ms)");


      Assert.assertTrue(hasResultSet);
      int cnt;
      try (ResultSet resultSet = statement.getResultSet()) {
        cnt = 0;
        while (resultSet.next()) {
          String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
              .getString(last_value("root.ln.wf01.wt01.temperature"))
              + "," + resultSet.getString(first_value("root.ln.wf01.wt01.temperature"));
          Assert.assertEquals(retArray1[cnt], ans);
          cnt++;
        }
        Assert.assertEquals(retArray1.length, cnt);
      }

      hasResultSet = statement.execute(
          "select first_value(temperature), last_value(temperature) from root.ln.wf01.wt01 "
              + "where temperature > 3 "
              + "GROUP BY ([2,300], 20ms)");

      Assert.assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        cnt = 0;
        while (resultSet.next()) {
          String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
              .getString(last_value("root.ln.wf01.wt01.temperature"))
              + "," + resultSet.getString(first_value("root.ln.wf01.wt01.temperature"));
          Assert.assertEquals(retArray2[cnt], ans);
          cnt++;
        }
        Assert.assertEquals(retArray2.length, cnt);
      }

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void largeIntervalTest() throws SQLException {
    String[] retArray1 = new String[]{
            "0,4.4,12,300,4",
            "340,100.1,10,620,500",
            "680,null,0,null,null"
    };
    String[] retArray2 = new String[]{
            "0,3.3,13,300,3",
            "340,100.1,10,620,500",
            "680,null,0,null,null"
    };

    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select min_value(temperature), count(temperature), max_time(temperature), "
              + "min_time(temperature) from root.ln.wf01.wt01 where time > 3 GROUP BY "
              + "([0, 680], 340ms)");

      Assert.assertTrue(hasResultSet);
      int cnt;
      try (ResultSet resultSet = statement.getResultSet()) {
        cnt = 0;
        while (resultSet.next()) {
          String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
              .getString(min_value("root.ln.wf01.wt01.temperature"))
              + "," + resultSet.getString(count("root.ln.wf01.wt01.temperature")) + "," +
              resultSet.getString(max_time("root.ln.wf01.wt01.temperature"))
              + "," + resultSet.getString(min_time("root.ln.wf01.wt01.temperature"));
          Assert.assertEquals(retArray1[cnt], ans);
          cnt++;
        }
        Assert.assertEquals(retArray1.length, cnt);
      }

      hasResultSet = statement.execute(
          "select min_value(temperature), count (temperature), max_time(temperature), "
              + "min_time(temperature) from root.ln.wf01.wt01 where temperature > 3 GROUP BY "
              + "([0, 680], 340ms)");

      Assert.assertTrue(hasResultSet);
      try (ResultSet resultSet = statement.getResultSet()) {
        cnt = 0;
        while (resultSet.next()) {
          String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
              .getString(min_value("root.ln.wf01.wt01.temperature"))
              + "," + resultSet.getString(count("root.ln.wf01.wt01.temperature")) + ","
              + resultSet.getString(max_time("root.ln.wf01.wt01.temperature"))
              + "," + resultSet.getString(min_time("root.ln.wf01.wt01.temperature"));
          Assert.assertEquals(retArray2[cnt], ans);
          cnt++;
        }
        Assert.assertEquals(retArray2.length, cnt);
      }

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void countSumAvgInnerIntervalTest() {
    String[] retArray1 = new String[]{
            "0,2,7.7,3.85",
            "30,1,30.3,30.3",
            "60,0,0.0,null",
            "90,0,0.0,null",
            "120,0,0.0,null",
            "150,1,200.2,200.2",
            "180,0,0.0,null",
            "210,0,0.0,null",
            "240,0,0.0,null",
            "270,0,0.0,null",
            "300,1,500.5,500.5",
            "330,0,0.0,null",
            "360,0,0.0,null",
            "390,0,0.0,null",
            "420,0,0.0,null",
            "450,0,0.0,null",
            "480,0,0.0,null",
            "510,1,200.2,200.2",
            "540,1,500.5,500.5",
            "570,0,0.0,null",
            "600,1,300.3,300.3"
    };

    try (Connection connection = DriverManager.
            getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root");
         Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
              "select count(temperature), sum(temperature), avg(temperature) from "
                      + "root.ln.wf01.wt01 where temperature > 3 "
                      + "GROUP BY ([0, 600], 5ms, 30ms)");

      Assert.assertTrue(hasResultSet);
      int cnt;
      try (ResultSet resultSet = statement.getResultSet()) {
        cnt = 0;
        while (resultSet.next()) {
          String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
                  .getString(count("root.ln.wf01.wt01.temperature")) + "," +
                  resultSet.getString(sum("root.ln.wf01.wt01.temperature")) + "," + resultSet
                  .getString(avg("root.ln.wf01.wt01.temperature"));
          Assert.assertEquals(retArray1[cnt], ans);
          cnt++;
        }
        Assert.assertEquals(retArray1.length, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  private void prepareData() {
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root",
            "root");
        Statement statement = connection.createStatement();) {

      for (String sql : dataSet1) {
        statement.execute(sql);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
