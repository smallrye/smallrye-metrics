/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package io.smallrye.metrics;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.eclipse.microprofile.metrics.Tag;

/**
 * @author hrupp
 */
public class JmxWorker {

    private static final String PLACEHOLDER = "%s";
    private static MBeanServer mbs;
    private static JmxWorker worker;

    private JmxWorker() {
        /* singleton */ }

    public static JmxWorker instance() {
        if (worker == null) {
            worker = new JmxWorker();
            mbs = ManagementFactory.getPlatformMBeanServer();
        }

        return worker;
    }

    /**
     * Read a value from the MBeanServer
     *
     * @param mbeanExpression The expression to look for
     * @return The value of the Mbean attribute
     */
    public Number getValue(String mbeanExpression) {

        if (mbeanExpression == null) {
            throw new IllegalArgumentException("MBean Expression is null");
        }
        if (!mbeanExpression.contains("/")) {
            throw new IllegalArgumentException(mbeanExpression);
        }

        int slashIndex = mbeanExpression.indexOf('/');
        String mbean = mbeanExpression.substring(0, slashIndex);
        String attName = mbeanExpression.substring(slashIndex + 1);
        String subItem = null;
        if (attName.contains("#")) {
            int hashIndex = attName.indexOf('#');
            subItem = attName.substring(hashIndex + 1);
            attName = attName.substring(0, hashIndex);
        }

        try {
            ObjectName objectName = new ObjectName(mbean);
            Object attribute = mbs.getAttribute(objectName, attName);
            if (attribute instanceof Number) {
                return (Number) attribute;
            } else if (attribute instanceof CompositeData) {
                CompositeData compositeData = (CompositeData) attribute;
                return (Number) compositeData.get(subItem);
            } else {
                throw new IllegalArgumentException(mbeanExpression);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * We need to expand entries that are marked with the <b>multi</b> flag
     * into the actual MBeans. This is done by replacing a placeholder of <b>%s</b>
     * in the name and MBean name with the real Mbean key-value.
     *
     * @param entries List of entries
     */
    public void expandMultiValueEntries(List<ExtendedMetadataAndTags> entries) {
        List<ExtendedMetadataAndTags> result = new ArrayList<>();
        List<ExtendedMetadataAndTags> toBeRemoved = new ArrayList<>(entries.size());
        for (ExtendedMetadataAndTags entry : entries) {
            if (entry.getMetadata().isMulti()) {
                String name = entry.getMetadata().getMbean();
                String attName;
                String queryableName;
                int slashIndex = name.indexOf('/');

                // MBeanName is invalid, lets skip this altogether
                if (slashIndex < 0) {
                    toBeRemoved.add(entry);
                    continue;
                }

                queryableName = name.substring(0, slashIndex);
                attName = name.substring(slashIndex + 1);

                try {
                    ObjectName objectNameWithPlaceholders = new ObjectName(queryableName);
                    final Map<String, String> keyHolders = findComponentPairsWithPlaceholders(objectNameWithPlaceholders);

                    ObjectName objectName = new ObjectName(queryableName.replaceAll(PLACEHOLDER + "(\\d)?+", "*"));

                    Set<ObjectName> objNames = mbs.queryNames(objectName, null);
                    for (ObjectName oName : objNames) {
                        String newName = entry.getMetadata().getName();
                        if (!newName.contains(PLACEHOLDER) && entry.getTags().isEmpty()) {
                            SmallRyeMetricsLogging.log.nameDoesNotContainPlaceHoldersOrTags(newName);
                        }
                        String newDisplayName = entry.getMetadata().getDisplayName();
                        String newDescription = entry.getMetadata().getDescription();
                        List<Tag> newTags = new ArrayList<>(entry.getTags());
                        for (final Entry<String, String> keyHolder : keyHolders.entrySet()) {
                            String keyValue = oName.getKeyPropertyList().get(keyHolder.getValue());
                            newName = newName.replaceAll(Pattern.quote(keyHolder.getKey()), keyValue);
                            newDisplayName = newDisplayName.replaceAll(Pattern.quote(keyHolder.getKey()), keyValue);
                            newDescription = newDescription.replaceAll(Pattern.quote(keyHolder.getKey()), keyValue);
                            newTags = newTags.stream()
                                    .map(originalTag -> new Tag(originalTag.getTagName(),
                                            originalTag.getTagValue().replaceAll(Pattern.quote(keyHolder.getKey()), keyValue)))
                                    .collect(Collectors.toList());
                        }

                        String newObjectName = oName.getCanonicalName() + "/" + attName;

                        ExtendedMetadata newEntryMetadata = new ExtendedMetadata(newName, newDisplayName, newDescription,
                                entry.getMetadata().getTypeRaw(), entry.getMetadata().getUnit(), newObjectName,
                                true);
                        ExtendedMetadataAndTags newEntry = new ExtendedMetadataAndTags(newEntryMetadata, newTags);
                        result.add(newEntry);
                    }
                    toBeRemoved.add(entry);
                } catch (MalformedObjectNameException e) {
                    throw SmallRyeMetricsMessages.msg.malformedObjectName(e);
                }
            }
        }
        entries.removeAll(toBeRemoved);
        entries.addAll(result);
    }

    /**
     * Takes an ObjectName and returns the name components that contain a placeholder (%s1, %s2,...) in their value.
     */
    private Map<String, String> findComponentPairsWithPlaceholders(ObjectName objectName) {
        return objectName.getKeyPropertyList().entrySet().stream()
                .filter(entry -> entry.getValue().matches(PLACEHOLDER + "(\\d)?+"))
                .collect(Collectors.toMap(
                        Entry::getValue,
                        Entry::getKey));
    }
}
