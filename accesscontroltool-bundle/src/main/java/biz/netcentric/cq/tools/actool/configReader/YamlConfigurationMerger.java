/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import biz.netcentric.cq.tools.actool.validators.AceBeanValidator;
import biz.netcentric.cq.tools.actool.validators.AuthorizableValidator;
import biz.netcentric.cq.tools.actool.validators.ConfigurationsValidator;
import biz.netcentric.cq.tools.actool.validators.YamlConfigurationsValidator;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AceBeanValidatorImpl;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableMemberGroupsValidator;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableValidatorImpl;

public class YamlConfigurationMerger implements ConfigurationMerger {

    private static final Logger LOG = LoggerFactory.getLogger(YamlConfigurationMerger.class);

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.configReader.ConfigurationMeger# getMergedConfigurations(java.util.Map,
     * biz.netcentric.cq.tools.actool.installationhistory .AcInstallationHistoryPojo, biz.netcentric.cq.tools.actool.configReader.ConfigReader)
     */
    @Override
    public List getMergedConfigurations(
            final Map<String, String> newestConfigurations,
            final AcInstallationHistoryPojo history,
            final ConfigReader configReader) throws RepositoryException,
            AcConfigBeanValidationException {
        final List c = new ArrayList<Map>();
        final Map<String, Set<AuthorizableConfigBean>> mergedAuthorizablesMapfromConfig = new LinkedHashMap<String, Set<AuthorizableConfigBean>>();
        final Map<String, Set<AceBean>> mergedAceMapFromConfig = new LinkedHashMap<String, Set<AceBean>>();
        final Set<String> groupIdsFromAllConfig = new HashSet<String>(); // needed for
        // detection
        // of doubled
        // defined
        // groups in
        // configurations
        final Map<String, String> mergedTemplateConfigs = new HashMap<String, String>();
        final ConfigurationsValidator configurationsValidator = new YamlConfigurationsValidator();

        for (final Map.Entry<String, String> entry : newestConfigurations.entrySet()) {
            final String message = "start merging configuration data from: "
                    + entry.getKey();

            configurationsValidator
                    .validateMandatorySectionIdentifiersExistence(
                            entry.getValue(), entry.getKey());

            history.addMessage(message);
            final Yaml yaml = new Yaml();
            final List<LinkedHashMap> yamlList = (List<LinkedHashMap>) yaml
                    .load(entry.getValue());

            final Set<String> sectionIdentifiers = new LinkedHashSet<String>();

            // FIXME: Why doesn't this use YamlConfigReader?
            // put all section identifiers of current configuration into a set
            for (int i = 0; i < yamlList.size(); i++) {
                sectionIdentifiers.addAll(yamlList.get(i).keySet());
            }
            configurationsValidator.validateSectionIdentifiers(
                    sectionIdentifiers, entry.getKey());

            configurationsValidator.validateSectionContentExistence(
                    entry.getKey(), yamlList);

            // build AuthorizableConfigBeans from current configurations
            final AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl();
            final Map<String, Set<AuthorizableConfigBean>> authorizablesMapfromConfig = configReader
                    .getGroupConfigurationBeans(yamlList, authorizableValidator);
            final Set<String> groupIdsFromCurrentConfig = authorizablesMapfromConfig != null ? authorizablesMapfromConfig.keySet() : null;

            if (groupIdsFromCurrentConfig != null) {
                configurationsValidator.validateDoubleGroups(groupIdsFromAllConfig,
                        groupIdsFromCurrentConfig, entry.getKey());
                // add IDs from authorizables from current configuration to set
                groupIdsFromAllConfig.addAll(groupIdsFromCurrentConfig);
            }

            // build AceBeans from current configuration
            final AceBeanValidator aceBeanValidator = new AceBeanValidatorImpl(
                    groupIdsFromAllConfig);
            final Map<String, Set<AceBean>> aceMapFromConfig = configReader
                    .getAceConfigurationBeans(yamlList,
                            groupIdsFromAllConfig, aceBeanValidator);

            // add AuthorizableConfigBeans built from current configuration to
            // set containing AuthorizableConfigBeans from all configurations
            if (authorizablesMapfromConfig != null) {
                mergedAuthorizablesMapfromConfig.putAll(authorizablesMapfromConfig);
            }

            // add AceBeans built from current configuration to set containing
            // AceBeans from all configurations
            if (aceMapFromConfig != null) {
                mergedAceMapFromConfig.putAll(aceMapFromConfig);
            }

            // Add page creation template configs
            final YamlConfigReader yamlConfigReader = new YamlConfigReader();
            final Map<String, String> mappings = yamlConfigReader.getTemplateConfiguration(yamlList);
            if (mappings != null) {
                mergedTemplateConfigs.putAll(mappings);
            }
        }
        // set member groups
        final AuthorizableMemberGroupsValidator membersValidator = new AuthorizableMemberGroupsValidator();
        membersValidator.validate(mergedAuthorizablesMapfromConfig);
        c.add(mergedAuthorizablesMapfromConfig);
        c.add(mergedAceMapFromConfig);
        c.add(mergedTemplateConfigs);
        return c;
    }
}
