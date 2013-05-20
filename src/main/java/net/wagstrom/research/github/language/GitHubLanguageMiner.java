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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class GitHubLanguageMiner {
    private Logger logger = null;
    private WebClient wc;
    private Properties props;
    private Pattern rankMatch = null;
    private Pattern numReposMatch = null;
    private HtmlPage searchPage;
    private int httpDelay = 1000;
    
    public GitHubLanguageMiner() {
        logger = LoggerFactory.getLogger(GitHubLanguageMiner.class);
        wc = new WebClient(BrowserVersion.FIREFOX_3_6);
        wc.setThrowExceptionOnFailingStatusCode(false);
        wc.setJavaScriptEnabled(false);
        wc.setCssEnabled(false);
        props = GitHubLanguageMinerProperties.props();
        rankMatch = Pattern.compile("is the (#([0-9]+) )?most popular language on GitHub");
        numReposMatch = Pattern.compile("Repositories \\(([0-9]+)\\)");
        httpDelay = Integer.parseInt(props.getProperty(PropNames.HTTP_DELAY, Defaults.HTTP_DELAY));
    }

    public void run() {
        HashMap<String, ProjectRecord> projectRecords = new HashMap<String, ProjectRecord>();
        HashMap<String, HtmlAnchor> languageLinks = getLanguageLinks();
        
        String searchUrl = props.getProperty(PropNames.SEARCH_URL, Defaults.SEARCH_URL);
        try {
            searchPage = wc.getPage(searchUrl);
        } catch (IOException e) {
            logger.error("Unable to fetch search page {}:", searchUrl, e);
        }
        
        httpSleep();
        for (Map.Entry<String, HtmlAnchor> language : languageLinks.entrySet()) {
            String languageName = language.getKey();
            HtmlAnchor languageAnchor = language.getValue();
            projectRecords.put(languageName, fetchLanguage(languageName, languageAnchor));
            httpSleep();
        }
        DatabaseDriver db = new DatabaseDriver();
        db.saveProjectRecords(projectRecords);
        db.close();
    }

    private void httpSleep() {
        try {
            Thread.sleep(httpDelay);
        } catch (InterruptedException e) {
            logger.error("Interrupted during sleep: {}", e);
        }
    }

    /**
     * Visits https://github.com/langauges and gets all languages
     * 
     * This is a little specific to the current formatting of the
     * pages, but that's a problem with any scraper.
     */
    private HashMap<String, HtmlAnchor> getLanguageLinks() {
        String baseUrl = props.getProperty(PropNames.BASE_URL, Defaults.BASE_URL);
        HashMap<String, HtmlAnchor> languageLinks = new HashMap<String, HtmlAnchor>();
        try {
            HtmlPage page = wc.getPage(baseUrl);
            
            HtmlElement el = page.getElementById("languages");
            el = el.getFirstByXPath("//div[@class=\"all_languages\"]/div/ul");
            for (HtmlElement anchor : el.getHtmlElementsByTagName("a")) {
                logger.trace(anchor.asText());
                logger.trace(anchor.getAttribute("href"));
                if (anchor instanceof HtmlAnchor) {
                    languageLinks.put(anchor.asText(), (HtmlAnchor) anchor);
                } else {
                    logger.warn("Found a non-HtmlAnchor element: {}", anchor);
                }
            }
        } catch (MalformedURLException e) {
            logger.error("invalid url: {}", baseUrl, e);
        } catch (IOException e) {
            logger.error("IO exception: {}", e);
        }
        return languageLinks;
    }
    
    private ProjectRecord fetchLanguage(String name, HtmlAnchor anchor) {
        ProjectRecord pr = new ProjectRecord(name);
        
        HtmlPage page = null;
        try {
            httpSleep();
            page = anchor.click();
            logger.trace("Page URL: {}", page.getUrl());
            HtmlElement el = page.getFirstByXPath("//div[@class=\"pagehead\"]/descendant::h1/em");
            String textContent = el.getTextContent();
            Matcher m1 = rankMatch.matcher(textContent.trim());
            if (m1.matches()) {
                if (m1.groupCount() > 0) {
                    int rank = 1;
                    if (m1.group(2) != null) {
                        rank = Integer.parseInt(m1.group(2));
                    }
                    pr.setRank(rank);
                    logger.info("language {}: rank: {}", name, rank);
                }
            }
            pr.setMostWatchedToday(processTopLanguage(page, props.getProperty(PropNames.MOST_WATCHED_TODAY, Defaults.MOST_WATCHED_TODAY)));
            pr.setMostForkedToday(processTopLanguage(page, props.getProperty(PropNames.MOST_FORKED_TODAY, Defaults.MOST_FORKED_TODAY)));
            pr.setMostWatchedThisWeek(processTopLanguage(page, props.getProperty(PropNames.MOST_WATCHED_THIS_WEEK, Defaults.MOST_WATCHED_THIS_WEEK)));
            pr.setMostForkedThisWeek(processTopLanguage(page, props.getProperty(PropNames.MOST_FORKED_THIS_WEEK, Defaults.MOST_FORKED_THIS_WEEK)));
            pr.setMostWatchedThisMonth(processTopLanguage(page, props.getProperty(PropNames.MOST_WATCHED_THIS_MONTH, Defaults.MOST_WATCHED_THIS_MONTH)));
            pr.setMostForkedThisMonth(processTopLanguage(page, props.getProperty(PropNames.MOST_FORKED_THIS_MONTH, Defaults.MOST_FORKED_THIS_MONTH)));
            pr.setMostWatchedOverall(processTopLanguage(page, props.getProperty(PropNames.MOST_WATCHED_OVERALL, Defaults.MOST_WATCHED_OVERALL)));
            pr.setMostForkedOverall(processTopLanguage(page, props.getProperty(PropNames.MOST_FORKED_OVERALL, Defaults.MOST_FORKED_OVERALL)));
            pr.setMostWatchedProjects(getMostWatchedProjects(page, Integer.parseInt(props.getProperty(PropNames.MOST_WATCHED_DEPTH, Defaults.MOST_WATCHED_DEPTH)), pr));
        } catch (IOException e) {
            logger.error("IO exception fetching {}:", name, e);
        }
        return pr;
    }
    
    private Collection<String> processTopLanguage(HtmlPage page, String h3Tag) {
        ArrayList<String> topProjects = new ArrayList<String>();
        logger.info("searching for: {}", h3Tag);
        for (HtmlElement elem : page.getElementsByTagName("h3")) {
            if (! elem.getTextContent().trim().equals(h3Tag)) continue;
            // DomNode d = elem.getParentNode();
            for (Object el : elem.getParentNode().getByXPath("ul/li/a[@class=\"repo\"]")) {
                if (el instanceof HtmlAnchor) { 
                    topProjects.add(((HtmlAnchor) el).getHrefAttribute());
                } else {
                    logger.warn("Warning: element is not an HtmlAnchor: {}", el);
                }
            }
        }
        return topProjects;
    }
    
    private Collection<String> getMostWatchedProjects(HtmlPage page, int depth, ProjectRecord pr) {
        ArrayList <String> topProjects = new ArrayList<String>();
        int maxPage = 1;
        for (Object el : page.getByXPath("//ul[@class=\"tabnav-tabs\"]/li/a")) {
            if (el instanceof HtmlAnchor && ((HtmlAnchor) el).getTextContent().trim().equals(props.getProperty(PropNames.MOST_WATCHED, Defaults.MOST_WATCHED).trim())) {
                HtmlAnchor a = (HtmlAnchor) el;
                try {
                    httpSleep();
                    HtmlPage newPage = a.click();
                    topProjects.addAll(getMostWatchedProjectsIterator(newPage, depth-1));
                    for (Object pageLink : newPage.getByXPath("//div[@class=\"pagination\"]/a")) {
                        if (pageLink instanceof HtmlAnchor) {
                            try {
                                int pageNum = Integer.parseInt(((HtmlAnchor) pageLink).getTextContent().trim());
                                if (pageNum > maxPage) maxPage = pageNum;
                            } catch (NumberFormatException e) {
                                // do nothing...
                            }
                        }
                    }

                } catch (IOException e) {
                    logger.error("IO Exception clicking link: {}", a, e);
                }
            }
        }
        return topProjects;
    }

    private Collection<String> getMostWatchedProjectsIterator(HtmlPage page, int depth) {
        ArrayList <String> topProjects = new ArrayList<String>();
        logger.info("depth: {}", depth);
        
        // unfortunately the unordered list we're looking for has multiple css
        // classes, so this hack finds it anywhere in the string
        for (Object el : page.getByXPath("//ul[contains(concat(' ',@class,' '),' repolist ')]/li/h3/a")) {
            if (el instanceof HtmlAnchor) {
                topProjects.add(((HtmlAnchor) el).getHrefAttribute());
            } else {
                logger.warn("Warning: element is not an HtmlAnchor: {}", el);
            }
        }
        if (depth > 0) {
            HtmlAnchor nextAnchor = null;
            try {
                nextAnchor = page.getFirstByXPath("//div[@class=\"pagination\"]/a[@rel=\"next\"]");
                if (nextAnchor != null) { 
                    httpSleep();
                    HtmlPage nextPage = nextAnchor.click();
                    topProjects.addAll(getMostWatchedProjectsIterator(nextPage, depth-1));
                }
            } catch (IOException e) {
                logger.error("IO Exception clicking link: {}", nextAnchor, e);
            }
        }
        return topProjects;
    }
}

