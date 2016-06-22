/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.usc.irds.sparkler.plugin.regex;

import edu.usc.irds.sparkler.AbstractExtensionPoint;
import edu.usc.irds.sparkler.URLFilter;
import org.apache.nutch.util.URLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;


/**
 * Generic {@link URLFilter URL filter} based on regular
 * expressions.
 *
 * <p>
 * The regular expressions rules are expressed in a file.  </p>
 *
 * <p>
 * The format of this file is made of many rules (one per line):<br/>
 * <code>
 * [+-]&lt;regex&gt;
 * </code><br/>
 * where plus (<code>+</code>)means go ahead and index it and minus (
 * <code>-</code>)means no.
 * </p>
 *
 * @author J&eacute;r&ocirc;me Charron
 */
public abstract class RegexURLFilterBase extends AbstractExtensionPoint implements URLFilter {

    /** My logger */
    private final static Logger LOG = LoggerFactory.getLogger(RegexURLFilterBase.class);

    /** An array of applicable rules */
    private List<RegexRule> rules;


    /**
     * Constructs a new empty RegexURLFilterBase
     */
    public RegexURLFilterBase() {
        try {
            this.rules = readRules(getRulesReader());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Constructs a new RegexURLFilter and init it with a file of rules.
     *
     * @param filename
     *          is the name of rules file.
     */
    public RegexURLFilterBase(File filename) throws IOException,
            IllegalArgumentException {
        this(new FileReader(filename));
    }

    /**
     * Constructs a new RegexURLFilter and init it with a Reader of rules.
     *
     * @param reader
     *          is a reader of rules.
     */
    public RegexURLFilterBase(Reader reader) throws IOException,
            IllegalArgumentException {
        rules = readRules(reader);
    }

    /**
     * Creates a new {@link RegexRule}.
     *
     * @param sign
     *          of the regular expression. A <code>true</code> value means that
     *          any URL matching this rule must be included, whereas a
     *          <code>false</code> value means that any URL matching this rule
     *          must be excluded.
     * @param regex
     *          is the regular expression associated to this rule.
     */
    protected abstract RegexRule createRule(boolean sign, String regex);

    /**
     * Creates a new {@link RegexRule}.
     * @param
     *        sign of the regular expression.
     *        A <code>true</code> value means that any URL matching this rule
     *        must be included, whereas a <code>false</code>
     *        value means that any URL matching this rule must be excluded.
     * @param regex
     *        is the regular expression associated to this rule.
     * @param hostOrDomain
     *        the host or domain to which this regex belongs
     */
    protected abstract RegexRule createRule(boolean sign, String regex, String hostOrDomain);

    /**
     * Returns the name of the file of rules to use for a particular
     * implementation.
     *
     *          is the current configuration.
     * @return the name of the resource containing the rules to use.
     */
    protected abstract Reader getRulesReader() throws IOException;


    public boolean filter(String url, String parent) {
        String host = URLUtil.getHost(url);
        String domain = null;

        try {
            domain = URLUtil.getDomainName(url);
        } catch (MalformedURLException e) {
            // shouldnt happen here right?
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("URL belongs to host " + host + " and domain " + domain);
        }

        for (RegexRule rule : rules) {
            // Skip the skip for rules that don't share the same host and domain
            if (rule.hostOrDomain() != null &&
                    !rule.hostOrDomain().equals(host) &&
                    !rule.hostOrDomain().equals(domain)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skipping rule [" + rule.regex() + "] for host: " + rule.hostOrDomain());
                }
                continue;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Applying rule [" + rule.regex() + "] for host: " + host + " and domain " + domain);
            }

            if (rule.match(url)) {
                return rule.accept();
            }
        }
        return false;
    }

  /*
   * ------------------------------ * </implementation:Configurable> *
   * ------------------------------
   */

    /**
     * Read the specified file of rules.
     *
     * @param reader
     *          is a reader of regular expressions rules.
     * @return the corresponding {@RegexRule rules}.
     */
    private List<RegexRule> readRules(Reader reader) throws IOException,
            IllegalArgumentException {

        BufferedReader in = new BufferedReader(reader);
        List<RegexRule> rules = new ArrayList<RegexRule>();
        String line;
        String hostOrDomain = null;

        while ((line = in.readLine()) != null) {
            if (line.length() == 0) {
                continue;
            }
            char first = line.charAt(0);
            boolean sign = false;
            switch (first) {
                case '+':
                    sign = true;
                    break;
                case '-':
                    sign = false;
                    break;
                case ' ':
                case '\n':
                case '#': // skip blank & comment lines
                    continue;
                case '>':
                    hostOrDomain = line.substring(1).trim();
                    continue;
                case '<':
                    hostOrDomain = null;
                    continue;
                default:
                    throw new IOException("Invalid first character: " + line);
            }

            String regex = line.substring(1);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Adding rule [" + regex + "] for " + hostOrDomain);
            }
            RegexRule rule = createRule(sign, regex, hostOrDomain);
            rules.add(rule);
        }
        return rules;
    }

    /**
     * Filter the standard input using a RegexURLFilterBase.
     *
     * @param filter
     *          is the RegexURLFilterBase to use for filtering the standard input.
     * @param args
     *          some optional parameters (not used).
     */
    public static void main(RegexURLFilterBase filter, String args[])
            throws IOException, IllegalArgumentException {

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            if (filter.filter(line, null)) {
                System.out.print("+");
                System.out.println(line);
            } else {
                System.out.print("-");
                System.out.println(line);
            }
        }
    }


}