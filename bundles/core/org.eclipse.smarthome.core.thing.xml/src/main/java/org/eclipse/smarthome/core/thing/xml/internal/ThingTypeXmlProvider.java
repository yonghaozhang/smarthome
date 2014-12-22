/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.core.thing.xml.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.smarthome.config.core.ConfigDescription;
import org.eclipse.smarthome.config.core.ConfigDescriptionProvider;
import org.eclipse.smarthome.config.xml.XmlConfigDescriptionProvider;
import org.eclipse.smarthome.config.xml.osgi.XmlDocumentBundleTracker;
import org.eclipse.smarthome.config.xml.osgi.XmlDocumentProvider;
import org.eclipse.smarthome.core.thing.binding.ThingTypeProvider;
import org.eclipse.smarthome.core.thing.type.ChannelGroupType;
import org.eclipse.smarthome.core.thing.type.ChannelType;
import org.eclipse.smarthome.core.thing.type.ThingType;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.converters.ConversionException;


/**
 * The {@link ThingTypeXmlProvider} is responsible managing any created {@link ThingType} objects
 * by a {@link ThingDescriptionReader} for a certain bundle.
 * <p>
 * This implementation registers each {@link ThingType} object at the {@link ThingTypeProvider}
 * which is itself registered as service at the <i>OSGi</i> service registry. If a configuration
 * section is found, a {@link ConfigDescription} object is registered at the
 * {@link ConfigDescriptionProvider} which is itself registered as service at the <i>OSGi</i>
 * service registry.
 * <p>
 * The {@link ThingTypeXmlProvider} uses an internal cache consisting of {@link #thingTypeRefs},
 * {@link #channelGroupTypeRefs}, {@link #channelGroupTypes} and {@link #channelTypes}.
 * This cache is used to merge first the {@link ChannelType} definitions with the
 * {@link ChannelGroupTypeXmlResult} objects to create valid {@link ChannelGroupType} objects.
 * After that the {@link ChannelType} and the {@link ChannelGroupType} definitions are used
 * to merge with the {@link ThingTypeXmlResult} objects to create valid {@link ThingType} objects.
 * After the merge process has been finished, the cache is cleared again.
 * The merge process is started when {@link #addingFinished()} is invoked from the according
 * {@link XmlDocumentBundleTracker}. 
 * 
 * @author Michael Grammling - Initial Contribution
 * 
 * @see ThingTypeXmlProviderFactory
 */
public class ThingTypeXmlProvider implements XmlDocumentProvider<List<?>> {

    private Logger logger = LoggerFactory.getLogger(ThingTypeXmlProvider.class);

    private Bundle bundle;
    private XmlConfigDescriptionProvider configDescriptionProvider;
    private XmlThingTypeProvider thingTypeProvider;

    // temporary cache
    private List<ThingTypeXmlResult> thingTypeRefs;
    private List<ChannelGroupTypeXmlResult> channelGroupTypeRefs;
    private Map<String, ChannelGroupType> channelGroupTypes;
    private Map<String, ChannelType> channelTypes;


    public ThingTypeXmlProvider(Bundle bundle,
            XmlConfigDescriptionProvider configDescriptionProvider,
            XmlThingTypeProvider thingTypeProvider) throws IllegalArgumentException {

        if (bundle == null) {
            throw new IllegalArgumentException("The Bundle must not be null!");
        }

        if (configDescriptionProvider == null) {
            throw new IllegalArgumentException("The XmlConfigDescriptionProvider must not be null!");
        }

        if (thingTypeProvider == null) {
            throw new IllegalArgumentException("The XmlThingTypeProvider must not be null!");
        }

        this.bundle = bundle;
        this.configDescriptionProvider = configDescriptionProvider;
        this.thingTypeProvider = thingTypeProvider;

        this.thingTypeRefs = new ArrayList<>(10);
        this.channelGroupTypeRefs = new ArrayList<>(10);
        this.channelGroupTypes = new HashMap<>(10);
        this.channelTypes = new HashMap<>(10);
    }

    @Override
    public synchronized void addingObject(List<?> types) {
        if (types != null) {
            for (Object type : types) {
                if (type instanceof ThingTypeXmlResult) {
                    ThingTypeXmlResult typeResult = (ThingTypeXmlResult) type;
                    addConfigDescription(typeResult.getConfigDescription());
                    this.thingTypeRefs.add(typeResult);
                } else if (type instanceof ChannelGroupTypeXmlResult) {
                    ChannelGroupTypeXmlResult typeResult = (ChannelGroupTypeXmlResult) type;
                    this.channelGroupTypeRefs.add(typeResult);
                } else if (type instanceof ChannelTypeXmlResult) {
                    ChannelTypeXmlResult typeResult = (ChannelTypeXmlResult) type;
                    addConfigDescription(typeResult.getConfigDescription());
                    ChannelType channelType = typeResult.getChannelType();
                    this.channelTypes.put(channelType.getUID().toString(), channelType);
                } else {
                    throw new ConversionException("Unknown data type for '" + type + "'!");
                }
            }
        }
    }

    private void addConfigDescription(ConfigDescription configDescription) {
        if (configDescription != null) {
            try {
                this.configDescriptionProvider.addConfigDescription(
                        this.bundle, configDescription);
            } catch (Exception ex) {
                this.logger.error("Could not register ConfigDescription!", ex);
            }
        }
    }

    @Override
    public synchronized void addingFinished() {
        // create channel group types
        for (ChannelGroupTypeXmlResult type : this.channelGroupTypeRefs) {
            this.channelGroupTypes.put(type.getUID().toString(),
                    type.toChannelGroupType(this.channelTypes));
        }

        // create thing and bridge types
        for (ThingTypeXmlResult type : this.thingTypeRefs) {
            this.thingTypeProvider.addThingType(this.bundle,
                    type.toThingType(this.channelGroupTypes, this.channelTypes));
        }

        // release temporary cache
        this.thingTypeRefs.clear();
        this.channelGroupTypeRefs.clear();
        this.channelGroupTypes.clear();
        this.channelTypes.clear();
    }

    @Override
    public synchronized void release() {
        this.thingTypeProvider.removeAllThingTypes(this.bundle);
        this.configDescriptionProvider.removeAllConfigDescriptions(this.bundle);
    }

}
