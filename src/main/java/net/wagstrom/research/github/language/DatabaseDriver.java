/*
 * Copyright (c) 2012 IBM Corporation 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.wagstrom.research.github.language;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseDriver {
    
    private Connection connect = null;
    private Statement statement = null;
    private ResultSet resultSet = null;
    
    private Logger logger = null;
    
    private static final String DERBY_TABLE_EXISTS = "X0Y32";
    
    private static final String UPDATE_TABLE = "CREATE TABLE githubupdate" +
    		"(id int primary key generated always as identity," +
    		"create_date timestamp not null default CURRENT_TIMESTAMP)";
    private static final String LANGUAGE_TABLE = "CREATE TABLE proglang" +
    		"(id int primary key generated always as identity," +
    		"name varchar(64), create_date timestamp not null default CURRENT_TIMESTAMP)";
    private static final String REPOSITORY_TABLE = "CREATE TABLE repo" +
    		"(id int primary key generated always as identity," +
    		"username varchar(64), reponame varchar(64)," +
    		"create_date timestamp not null default CURRENT_TIMESTAMP)";
    private static final String TOP_CATEGORY_TABLE = "CREATE TABLE topcategory" +
    		"(id int primary key generated always as identity," +
    		"name varchar(64), create_date timestamp not null default CURRENT_TIMESTAMP)";
    private static final String PROJECT_UPDATE_TABLE = "CREATE TABLE repoupdate " +
    		"(id int primary key generated always as identity," +
    		"update_id int not null constraint projectupdate_githubupdate_fk references GITHUBUPDATE(id)," +
    		"proglang_id int not null constraint projectudpate_language_fk references PROGLANG(id)," +
    		"repo_id int not null constraint projectupdate_repository_fk references REPO(id)," +
    		"category_id int not null constraint projectupdate_topcategory_fk references TOPCATEGORY(id)," +
    		"rank int)";
    private static final String LANGUAGE_UPDATE = "CREATE TABLE languageupdate" +
    		"(id int primary key generated always as identity," +
            "update_id int not null constraint languageupdate_githubupdate_fk references GITHUBUPDATE(id)," +
    		"proglang_id int not null constraint languageupdate_language_fk references PROGLANG(id)," +
    		"num_projects int not null," +
    		"rank int not null)";
    
    private Properties props; 
    
    private HashMap<String, Integer> categoryMap = null;
    private HashMap<String, Integer> repositoryMap = null;
    
    public DatabaseDriver() {
        logger = LoggerFactory.getLogger(DatabaseDriver.class);
        props = GitHubLanguageMinerProperties.props();
        categoryMap = new HashMap<String, Integer>();
        repositoryMap = new HashMap<String, Integer>();
        try {

            Class.forName(props.getProperty(PropNames.JDBC_DRIVER, Defaults.JDBC_DRIVER)).newInstance();
            connect = DriverManager
                    .getConnection(props.getProperty(PropNames.JDBC_URL, Defaults.JDBC_URL));
            createTables();
        } catch (Exception e) {
            logger.error("Exception caught: ", e);
        }
    }

    public void close() {
        try {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
            if (connect != null) {
                connect.close();
            }
        } catch (SQLException e) {
            logger.error("SQLException caught closing database: ", e);
        }
    }
    
    public void createTables() {
        createTable(UPDATE_TABLE);
        createTable(LANGUAGE_TABLE);
        createTable(REPOSITORY_TABLE);
        createTable(TOP_CATEGORY_TABLE);
        createTable(PROJECT_UPDATE_TABLE);
        createTable(LANGUAGE_UPDATE);
    }
    
    private void createTable(String tableString) {
        try {
            Statement statement = connect.createStatement();
            statement.execute(tableString);
            statement.close();
        } catch (SQLException e) {
            if (e.getSQLState().equals(DERBY_TABLE_EXISTS)) {
                logger.info("Table already exists:\n{}", tableString);
            } else {
                logger.error("Error creating table state={}:\n{}", new Object[]{e.getSQLState(), tableString, e});
            }
        }
    }
    
    private int getLanguage(String language) {
        int rv = -1;
        try {
            PreparedStatement statement = connect.prepareStatement("SELECT id FROM proglang WHERE name=?");
            statement.setString(1, language);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                rv = rs.getInt("id");
                rs.close();
                statement.close();
                return rv;
            }
            rs.close();
            statement.close();
            return createLanguage(language);
        } catch (SQLException e) {
            logger.error("SQL exception getting language: {}", language, e);
        }
        return rv;
    }
    
    private int createLanguage(String language) {
        try {
            PreparedStatement statement = connect.prepareStatement("INSERT INTO proglang (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, language);
            statement.execute();
            ResultSet rs = statement.getGeneratedKeys();
            while (rs.next()) {
                int rv = rs.getInt(1);
                rs.close();
                statement.close();
                return rv;
            }
        } catch (SQLException e) {
            logger.error("SQL exception creating language: {}", language, e);
        }
        return -1;
    }

    private int createGitHubUpdate() {
        try {
            Statement statement = connect.createStatement();
            statement.execute("INSERT INTO githubupdate (create_date) VALUES (CURRENT_TIMESTAMP)",
                    Statement.RETURN_GENERATED_KEYS);
            ResultSet rs = statement.getGeneratedKeys();
            while (rs.next()) {
                int rv = rs.getInt(1);
                rs.close();
                statement.close();
                return rv;
            }
            rs.close();
            statement.close();
        } catch (SQLException e) {
            logger.error("SQL exception creating githubupdate:",e);
        }
        return -1;
    }

    private int createCategory(String categoryName) {
        try {
            PreparedStatement statement = connect.prepareStatement("INSERT INTO topcategory (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, categoryName);
            statement.execute();
            ResultSet rs = statement.getGeneratedKeys();
            while (rs.next()) {
                int rv = rs.getInt(1);
                rs.close();
                categoryMap.put(categoryName, rv);
                statement.close();
                return rv;
            }
            rs.close();
            statement.close();
        } catch (SQLException e) {
            logger.error("SQL exception creating category: {}", categoryName, e);
        }
        return -1;
    }

    private int getCategory(String categoryName) {
        if (categoryMap.containsKey(categoryName)) { return categoryMap.get(categoryName); }
        try {
            PreparedStatement statement = connect.prepareStatement("SELECT id FROM topcategory WHERE name=?");
            statement.setString(1, categoryName);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                int rv = rs.getInt("id");
                rs.close();
                categoryMap.put(categoryName, rv);
                statement.close();
                return rv;
            }
            rs.close();
            statement.close();
            return createCategory(categoryName);
        } catch (SQLException e) {
            logger.error("SQL exception getting category: {}", categoryName, e);
        }
        return -1;
    }
    
    private int createRepository(String username, String reponame) {
        try {
            PreparedStatement statement = connect.prepareStatement("INSERT INTO repo (username, reponame) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, reponame);
            statement.execute();
            ResultSet rs = statement.getGeneratedKeys();
            while (rs.next()) {
                int rv = rs.getInt(1);
                rs.close();
                repositoryMap.put(username + "/" + reponame, rv);
                statement.close();
                return rv;
            }
            rs.close();
            statement.close();
        } catch (SQLException e) {
            logger.error("SQL exception creating repository: {}/{}", new Object[]{username, reponame, e});
        }
        return -1;
    }

    private int getRepository(String repositoryName) {
        repositoryName = repositoryName.trim();
        if (repositoryName.startsWith("/")) {
            repositoryName = repositoryName.substring(1);
        }
        // FIXME: this really should do more checking here...
        String [] parts = repositoryName.split("/");
        return getRepository(parts[0], parts[1]);
    }
    
    private int getRepository(String username, String reponame) {
        if (repositoryMap.containsKey(username + "/" + reponame)) {
            return repositoryMap.get(username + "/" + reponame);
        }
        try {
            PreparedStatement statement = connect.prepareStatement("SELECT id FROM repo WHERE username=? AND reponame=?");
            statement.setString(1, username);
            statement.setString(2, reponame);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                int rv = rs.getInt("id");
                rs.close();
                repositoryMap.put(username + "/" + reponame, rv);
                statement.close();
                return rv;
            }
            rs.close();
            statement.close();
            return createRepository(username, reponame);
        } catch (SQLException e) {
            logger.error("SQL exception getting category: {}/{}", new Object[]{username, reponame, e});
        }
        return -1;
    }
    
    private void saveTopProjects(int update_id, int language_id, String categoryName, Collection<String> repositories) {
        int category_id = getCategory(categoryName);
        int ctr = 1;
        try {
            PreparedStatement statement = connect.prepareStatement("INSERT INTO repoupdate(update_id, proglang_id, repo_id, category_id, rank) VALUES (?, ?, ?, ?, ?)");
            for (String s : repositories) {
                int repository_id = getRepository(s);
                statement.setInt(1, update_id);
                statement.setInt(2, language_id);
                statement.setInt(3, repository_id);
                statement.setInt(4, category_id);
                statement.setInt(5, ctr++);
                statement.execute();
            }
            statement.close();
        } catch (SQLException e) {
            logger.error("SQL exception saving top projects language: {}, category: {}", new Object[]{language_id, categoryName, e});
        }
    }

    private void saveLanguageUpdate(int update_id, int language_id, int num_projects, int rank) {
        try {
            PreparedStatement statement = connect.prepareStatement("INSERT INTO languageupdate(update_id, proglang_id, num_projects, rank) VALUES (?, ?, ?, ?)");
            statement.setInt(1, update_id);
            statement.setInt(2, language_id);
            statement.setInt(3, num_projects);
            statement.setInt(4, rank);
            statement.execute();
            statement.close();
        } catch (SQLException e) {
            logger.error("SQL exception saving language update proglang: {}, update: {}, num_projects: {}, rank: {}", new Object[]{language_id, update_id, num_projects, rank, e});
        }
    }
    
    public void saveProjectRecords(HashMap<String, ProjectRecord> records) {
        int update_id = createGitHubUpdate();
        logger.info("update id: {}", update_id);
        for (Map.Entry<String, ProjectRecord> language : records.entrySet()) {
            int language_id = getLanguage(language.getKey());
            ProjectRecord record = language.getValue();
            saveTopProjects(update_id, language_id, Defaults.MOST_WATCHED, record.getMostWatchedProjects());
            saveTopProjects(update_id, language_id, Defaults.MOST_WATCHED_TODAY, record.getMostWatchedToday());
            saveTopProjects(update_id, language_id, Defaults.MOST_FORKED_TODAY, record.getMostForkedToday());
            saveTopProjects(update_id, language_id, Defaults.MOST_WATCHED_THIS_WEEK, record.getMostWatchedThisWeek());
            saveTopProjects(update_id, language_id, Defaults.MOST_FORKED_THIS_WEEK, record.getMostForkedThisWeek());
            saveTopProjects(update_id, language_id, Defaults.MOST_WATCHED_THIS_MONTH, record.getMostWatchedThisMonth());
            saveTopProjects(update_id, language_id, Defaults.MOST_FORKED_THIS_MONTH, record.getMostForkedThisMonth());
            saveTopProjects(update_id, language_id, Defaults.MOST_WATCHED_OVERALL, record.getMostWatchedOverall());
            saveTopProjects(update_id, language_id, Defaults.MOST_FORKED_OVERALL, record.getMostForkedOverall());
            saveLanguageUpdate(update_id, language_id, record.getNumProjects(), record.getRank());
        }
    }
}
