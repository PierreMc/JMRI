package jmri.jmrit.operations.trains;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jmri.InstanceManager;
import jmri.jmrit.operations.locations.Track;
import jmri.jmrit.operations.rollingstock.cars.Car;
import jmri.jmrit.operations.rollingstock.engines.Engine;
import jmri.jmrit.operations.routes.RouteLocation;
import jmri.jmrit.operations.setup.Setup;
import jmri.jmrit.operations.trains.trainbuilder.TrainCommon;
import jmri.server.json.JSON;
import jmri.server.json.operations.JsonOperations;
import jmri.server.json.operations.JsonUtil;

/**
 * A minimal manifest in JSON.
 *
 * This manifest is intended to be read by machines for building manifests in
 * other, human-readable outputs. This manifest is retained at build time so
 * that manifests can be endlessly recreated in other formats, even if the
 * operations database state has changed. It is expected that the parsers for
 * this manifest will be capable of querying operations for more specific
 * information while transforming this manifest into other formats.
 *
 * @author Randall Wood
 * @author Daniel Boudreau 1/26/2015 Load all cars including utility cars into
 * the JSON file, and tidied up the code a bit.
 *
 */
public class JsonManifest extends TrainCommon {

    protected final Locale locale = Locale.getDefault();
    protected final Train train;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonUtil utilities = new JsonUtil(mapper);

    private final static Logger log = LoggerFactory.getLogger(JsonManifest.class);

    public JsonManifest(Train train) {
        this.train = train;
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public File getFile() {
        return InstanceManager.getDefault(TrainManagerXml.class).getManifestFile(this.train.getName(), JSON.JSON);
    }

    public void build() throws IOException {
        ObjectNode root = this.mapper.createObjectNode();
        if (!this.train.getRailroadName().equals(Train.NONE)) {
            root.put(JSON.RAILROAD, StringEscapeUtils.escapeHtml4(this.train.getRailroadName()));
        } else {
            root.put(JSON.RAILROAD, StringEscapeUtils.escapeHtml4(Setup.getRailroadName()));
        }
        root.put(JSON.USERNAME, StringEscapeUtils.escapeHtml4(this.train.getName()));
        root.put(JSON.DESCRIPTION, StringEscapeUtils.escapeHtml4(this.train.getDescription()));
        root.set(JSON.LOCATIONS, this.getLocations());
        if (!this.train.getManifestLogoPathName().equals(Train.NONE)) {
            // The operationsServlet will need to change this to a usable URL
            root.put(JSON.IMAGE, this.train.getManifestLogoPathName());
        }
        root.put(JsonOperations.DATE, TrainCommon.getISO8601Date(true)); // Validity
        this.mapper.writeValue(InstanceManager.getDefault(TrainManagerXml.class).createManifestFile(this.train.getName(), JSON.JSON), root);
    }

    public ArrayNode getLocations() {
        // get engine and car lists
        List<Engine> engineList = engineManager.getByTrainBlockingList(train);
        List<Car> carList = carManager.getByTrainDestinationList(train);
        ArrayNode locations = this.mapper.createArrayNode();
        List<RouteLocation> route = train.getRoute().getLocationsBySequenceList();
        for (RouteLocation routeLocation : route) {
            String locationName = routeLocation.getSplitName();
            ObjectNode jsonLocation = this.mapper.createObjectNode();
            ObjectNode jsonCars = this.mapper.createObjectNode();
            jsonLocation.put(JSON.USERNAME, StringEscapeUtils.escapeHtml4(locationName));
            jsonLocation.put(JSON.NAME, routeLocation.getId());
            if (routeLocation != train.getTrainDepartsRouteLocation()) {
                jsonLocation.put(JSON.ARRIVAL_TIME, train.getExpectedArrivalTime(routeLocation));
            }
            if (routeLocation == train.getTrainDepartsRouteLocation()) {
                jsonLocation.put(JSON.DEPARTURE_TIME, train.getDepartureTime());
            } else if (!routeLocation.getDepartureTime().equals(RouteLocation.NONE)) {
                jsonLocation.put(JSON.DEPARTURE_TIME, routeLocation.getDepartureTime());
            } else {
                jsonLocation.put(JSON.EXPECTED_DEPARTURE, train.getExpectedDepartureTime(routeLocation));
            }
            // add location comment and id
            ObjectNode locationNode = this.mapper.createObjectNode();
            locationNode.put(JSON.COMMENT,
                    StringEscapeUtils.escapeHtml4(routeLocation.getLocation().getCommentWithColor()));
            locationNode.put(JSON.NAME, routeLocation.getLocation().getId());
            jsonLocation.set(JSON.LOCATION, locationNode);
            jsonLocation.put(JSON.COMMENT, StringEscapeUtils.escapeHtml4(routeLocation.getCommentWithColor()));
            // engine change or helper service?
            if (train.getSecondLegOptions() != Train.NO_CABOOSE_OR_FRED) {
                ArrayNode options = this.mapper.createArrayNode();
                if (routeLocation == train.getSecondLegStartRouteLocation()) {
                    if ((train.getSecondLegOptions() & Train.HELPER_ENGINES) == Train.HELPER_ENGINES) {
                        options.add(JSON.ADD_HELPERS);
                    } else if ((train.getSecondLegOptions() & Train.REMOVE_CABOOSE) == Train.REMOVE_CABOOSE
                            || (train.getSecondLegOptions() & Train.ADD_CABOOSE) == Train.ADD_CABOOSE) {
                        options.add(JSON.CHANGE_CABOOSE);
                    } else if ((train.getSecondLegOptions() & Train.CHANGE_ENGINES) == Train.CHANGE_ENGINES) {
                        options.add(JSON.CHANGE_ENGINES);
                    }
                }
                if (routeLocation == train.getSecondLegEndRouteLocation()) {
                    options.add(JSON.REMOVE_HELPERS);
                }
                jsonLocation.set(JSON.OPTIONS, options);
            }
            if (train.getThirdLegOptions() != Train.NO_CABOOSE_OR_FRED) {
                ArrayNode options = this.mapper.createArrayNode();
                if (routeLocation == train.getThirdLegStartRouteLocation()) {
                    if ((train.getThirdLegOptions() & Train.HELPER_ENGINES) == Train.HELPER_ENGINES) {
                        options.add(JSON.ADD_HELPERS);
                    } else if ((train.getThirdLegOptions() & Train.REMOVE_CABOOSE) == Train.REMOVE_CABOOSE
                            || (train.getThirdLegOptions() & Train.ADD_CABOOSE) == Train.ADD_CABOOSE) {
                        options.add(JSON.CHANGE_CABOOSE);
                    } else if ((train.getThirdLegOptions() & Train.CHANGE_ENGINES) == Train.CHANGE_ENGINES) {
                        options.add(JSON.CHANGE_ENGINES);
                    }
                }
                if (routeLocation == train.getThirdLegEndRouteLocation()) {
                    options.add(JSON.ADD_HELPERS);
                }
                jsonLocation.set(JSON.OPTIONS, options);
            }

            ObjectNode engines = this.mapper.createObjectNode();
            engines.set(JSON.ADD, pickupEngines(engineList, routeLocation));
            engines.set(JSON.REMOVE, dropEngines(engineList, routeLocation));
            jsonLocation.set(JSON.ENGINES, engines);

            // block cars by destination
            // caboose or FRED is placed at end of the train
            // passenger cars are already blocked in the car list
            // passenger cars with negative block numbers are placed at
            // the front of the train, positive numbers at the end of
            // the train.
            ArrayNode pickups = this.mapper.createArrayNode();
            for (RouteLocation destination : route) {
                for (Car car : carList) {
                    if (isNextCar(car, routeLocation, destination)) {
                        pickups.add(this.utilities.getCar(car, locale));
                    }
                }
            }
            jsonCars.set(JSON.ADD, pickups);
            // car set outs
            ArrayNode setouts = this.mapper.createArrayNode();
            for (Car car : carList) {
                if (car.getRouteDestination() == routeLocation) {
                    setouts.add(this.utilities.getCar(car, locale));
                }
            }
            jsonCars.set(JSON.REMOVE, setouts);

            jsonLocation.set(JSON.TRACK, this.getTrackComments(routeLocation, carList));

            if (routeLocation != train.getTrainTerminatesRouteLocation()) {
                jsonLocation.put(JSON.TRAIN_DIRECTION, routeLocation.getTrainDirection());
                ObjectNode length = this.mapper.createObjectNode();
                length.put(JSON.LENGTH, train.getTrainLength(routeLocation));
                length.put(JSON.UNIT, Setup.getLengthUnit());
                jsonLocation.set(JSON.LENGTH, length);
                jsonLocation.put(JSON.WEIGHT, train.getTrainWeight(routeLocation));
                int cars = train.getNumberCarsInTrain(routeLocation);
                int emptyCars = train.getNumberEmptyCarsInTrain(routeLocation);
                jsonCars.put(JSON.TOTAL, cars);
                jsonCars.put(JSON.LOADS, cars - emptyCars);
                jsonCars.put(JSON.EMPTIES, emptyCars);
            } else {
                log.debug("Train terminates in {}", locationName);
                jsonLocation.put("TrainTerminatesIn", StringEscapeUtils.escapeHtml4(locationName));
            }
            jsonLocation.set(JsonOperations.CARS, jsonCars);
            locations.add(jsonLocation);
        }
        return locations;
    }

    protected ArrayNode dropEngines(List<Engine> engines, RouteLocation routeLocation) {
        ArrayNode node = this.mapper.createArrayNode();
        for (Engine engine : engines) {
            if (engine.getRouteDestination() != null && engine.getRouteDestination().equals(routeLocation)) {
                node.add(this.utilities.getEngine(engine, locale));
            }
        }
        return node;
    }

    protected ArrayNode pickupEngines(List<Engine> engines, RouteLocation routeLocation) {
        ArrayNode node = this.mapper.createArrayNode();
        for (Engine engine : engines) {
            if (engine.getRouteLocation() != null && engine.getRouteLocation().equals(routeLocation)) {
                node.add(this.utilities.getEngine(engine, locale));
            }
        }
        return node;
    }

    // TODO: migrate comments into actual setout/pickup track location spaces
    private ObjectNode getTrackComments(RouteLocation routeLocation, List<Car> cars) {
        ObjectNode comments = this.mapper.createObjectNode();
        if (routeLocation.getLocation() != null) {
            List<Track> tracks = routeLocation.getLocation().getTracksByNameList(null);
            for (Track track : tracks) {
                ObjectNode jsonTrack = this.mapper.createObjectNode();
                    jsonTrack.put(JSON.ADD, StringEscapeUtils.escapeHtml4(track.getCommentPickupWithColor()));
                    jsonTrack.put(JSON.REMOVE, StringEscapeUtils.escapeHtml4(track.getCommentSetoutWithColor()));
                    jsonTrack.put(JSON.ADD_AND_REMOVE, StringEscapeUtils.escapeHtml4(track.getCommentBothWithColor()));
                    jsonTrack.put(JSON.COMMENT, StringEscapeUtils.escapeHtml4(track.getComment()));
                    comments.set(track.getId(), jsonTrack);
            }
        }
        return comments;
    }
}
