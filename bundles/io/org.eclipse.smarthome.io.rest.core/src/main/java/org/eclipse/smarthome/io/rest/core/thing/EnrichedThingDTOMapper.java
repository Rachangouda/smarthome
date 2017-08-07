/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.io.rest.core.thing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.dto.ChannelDTO;
import org.eclipse.smarthome.core.thing.dto.ThingDTO;
import org.eclipse.smarthome.core.thing.dto.ThingDTOMapper;
import org.eclipse.smarthome.core.thing.firmware.dto.FirmwareStatusDTO;

/**
 * The {@link EnrichedThingDTOMapper} is an utility class to map things into enriched thing data transfer objects
 * (DTOs).
 *
 * @author Dennis Nobel - Initial contribution
 */
public class EnrichedThingDTOMapper extends ThingDTOMapper {

    /**
     * Maps thing into enriched thing data transfer object.
     *
     * @param thing the thing
     * @param thingStatusInfo the thing status information to be used for the enriched object
     * @param firmwareStatus the firmwareStatus to be used for the enriched object
     * @param linkedItemsMap the map of linked items to be injected into the enriched object
     * @param editable true if this thing can be edited
     *
     * @return the enriched thing DTO object
     */
    public static EnrichedThingDTO map(Thing thing, ThingStatusInfo thingStatusInfo, FirmwareStatusDTO firmwareStatus,
            Map<String, Set<String>> linkedItemsMap, boolean editable) {
        ThingDTO thingDTO = ThingDTOMapper.map(thing);

        List<ChannelDTO> channels = new ArrayList<>();
        for (ChannelDTO channel : thingDTO.channels) {
            Set<String> linkedItems = linkedItemsMap != null ? linkedItemsMap.get(channel.id) : Collections.emptySet();
            channels.add(new EnrichedChannelDTO(channel, linkedItems));
        }

        return new EnrichedThingDTO(thingDTO, channels, thingStatusInfo, firmwareStatus, editable);
    }

}
