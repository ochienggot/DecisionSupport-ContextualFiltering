/**
 *
 */
package org.insight_centre.urq.citypulse.wp5.contextual_filter.mockup;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;

import javax.xml.bind.DatatypeConverter;

import org.postgresql.geometric.PGpoint;

import citypulse.commons.contextual_filtering.city_event_ontology.CityEvent;
import citypulse.commons.contextual_filtering.city_event_ontology.EventCategory;
import citypulse.commons.contextual_filtering.city_event_ontology.EventSource;
import citypulse.commons.contextual_filtering.contextual_event_request.Area;
import citypulse.commons.contextual_filtering.contextual_event_request.Place;
import citypulse.commons.contextual_filtering.contextual_event_request.PlaceAdapter;
import citypulse.commons.contextual_filtering.contextual_event_request.PlaceType;
import citypulse.commons.contextual_filtering.contextual_event_request.Point;
import citypulse.commons.contextual_filtering.contextual_event_request.Route;
import citypulse.commons.data.Coordinate;
import citypulse.commons.reasoning_request.Answer;
import citypulse.commons.reasoning_request.concrete.AnswerAdapter;
import citypulse.commons.reasoning_request.concrete.AnswerTravelPlanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDF;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import eu.citypulse.uaso.gdiclient.CpGdiInterface;
import eu.citypulse.uaso.gdiclient.persistdata.CpGdiPersistable;

/**
 * @author Thu-Le Pham
 * @date 9 Oct 2015
 */
public class EventDetection {


	public static void main(String[] args) {

		/*
		 * get route example
		 */
		// final Route route = loadRouteExample(Configuration.getInstance()
		// .getCFResourceFolderPath() + "RouteExample.txt");

		// /*
		// * generate sensors coordinate "closed" to route
		// */
		// final List<Coordinate> sensorsCoor = generateSensorsOnRoute(route);
		//
		// /*
		// * register event streams into GDI
		// */
		// registerEventStream(sensorsCoor);

		/*
		 * get information that is registered to GDI & publish events
		 */
		// final List<String[]> exchangeTopics = getEventStream(route);


		// /*
		// * map eventCoordinates with Route coordinates
		// */
		// mapEventCoordinateWithRoute(eventCoors, route);
		generateCityEvent("http://purl.oclc.org/NET/UNIS/sao/sao#df3d2809-4055-4322-838a-dafad56f2fb5",
				"http://purl.oclc.org/NET/UNIS/sao/ec#PublicParking",
				EventSource.SENSOR, new Point(10.21353, 56.15659));
	}

	public static String generateCityEvent(String eventId,
			String eventCategory, EventSource eventSource, Place place) {
		final Random r = new Random();

		final CityEvent event = new CityEvent();
		event.setEventId(eventId);
		event.setEventCategory(new EventCategory(eventCategory));
		event.setEventPlace(place);
		event.setEventSource(eventSource);
		event.setEventTime(System.currentTimeMillis());
		event.setEventLevel(r.nextInt(3) + 1);

		/*
		 * Convert CityEvent to GsonString
		 */
		final GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(Place.class, new PlaceAdapter());
		final Gson gson = builder.create();

		final String gsonStr = gson.toJson(event);
		System.out.println(gsonStr);
		return gsonStr;
	}

	public static void publishEvent(List<String[]> exchanges_topics,
			List<Coordinate> eventCoordinate) {

		/*
		 * example of producer
		 */

		final ConnectionFactory factory = new ConnectionFactory();
		final String uri = "amqp://guest:guest@127.0.0.1:5672";
		// factory.setHost("localhost");
		Connection conn;
		try {
			factory.setUri(uri);
			conn = factory.newConnection();
			final Channel channel = conn.createChannel();
			System.out.println("Producer channel opened.");

			final AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties()
					.builder();
			final String[] eventCategories = { "TrafficJam", "PublicParking",
					"HotWeather", "CarAccident" };

			final Random r = new Random();
			for (int t = 0; t < exchanges_topics.size(); t++) {
				final String[] et = exchanges_topics.get(t);
				final String exchange = et[0];
				final String topic = et[1];
				channel.exchangeDeclare(exchange, "direct");
				// publish message
				for (int i = 1; i <= 3; i++) {
					// final String event = generateCityEvent(
					// "event" + r.nextInt(),
					// eventCategories[r.nextInt(4)],
					// EventSource.values()[i % 2], new Point(
					// eventCoordinate.get(t)));
					final String event = generateRDFEvent(
							eventCategories[r.nextInt(4)],
							"TransportationEvent", eventCoordinate.get(t));
					channel.basicPublish(exchange, topic, builder.build(),
							event.getBytes());

					System.out.println("Published message = " + event);
					Thread.sleep(1000);
				}

			}
		} catch (final IOException | InterruptedException
				| KeyManagementException | NoSuchAlgorithmException
				| URISyntaxException e) {
			e.printStackTrace();
		}
	}

	public static Route loadRouteExample(String routeExampleFile) {
		final Route route = new Route();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(routeExampleFile));
			final StringBuilder sb = new StringBuilder();
			String line = br.readLine();
			while (line != null) {
				sb.append(line);
				line = br.readLine();
			}
			final GsonBuilder builder = new GsonBuilder();

			builder.registerTypeAdapter(Answer.class, new AnswerAdapter());

			final Gson gson = builder.create();

			final AnswerTravelPlanner answer = gson.fromJson(sb.toString(),
					AnswerTravelPlanner.class);
			route.setPlaceId("" + answer.getId());
			route.setRoute(answer.getRoute());
			route.setLength(answer.getLength());


		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				br.close();
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Example route = " + route);
		return route;
	}

	public static List<Coordinate> generateSensorsOnRoute(Route route) {
		final List<Coordinate> sensorsCoor = new ArrayList<Coordinate>();
		final Random r = new Random();
		for (final Coordinate coor : route.getRoute()) {
			if (r.nextInt() % 5 == 0) {
				sensorsCoor.add(coor);
			} else {
				double m = r.nextInt(7000 - 1000 + 1) + 1000;
				m = m / 10000000;
				final Coordinate newcoor = new Coordinate(coor.getLongitude()
						- m, coor.getLatitude() - m);
				sensorsCoor.add(newcoor);
				// System.out.println(coor.distance(newcoor));
			}
		}

		return sensorsCoor;
	}

	public static void registerEventStream(List<Coordinate> sensorsStreams) {
		final List<String> routingKeys = new ArrayList<String>();
		routingKeys.add("7bf9af8f-68cf-5acc-b20c-4c95741b945e");
		routingKeys.add("47e18070-bac6-542a-a0ec-c91c90c2424a");
		routingKeys.add("47e18070-bac6-542a-a0ec-c91c90c2424a");
		routingKeys.add("c5eecc91-63fb-5bde-9db0-9064e91b32a6");
		routingKeys.add("76fb5607-a05c-517b-a734-3abc91d8c29c");
		routingKeys.add("a1fb50f5-e2bd-57d3-a5be-85eb977b19cf");
		routingKeys.add("17371264-04fa-5fda-aedc-36c46476bbbf");
		routingKeys.add("e76f62be-c03b-5313-8dae-7a18f4480680");
		routingKeys.add("4f3823ea-7067-5bca-964a-3d17ac33161a");
		routingKeys.add("2f7ec3a0-e4ba-500f-b28b-f18b09c77644");
		System.out.println("Number of routing keys = " + routingKeys.size());

		CpGdiInterface cgi;
		try {
			cgi = new CpGdiInterface("127.0.0.1", 5432);
			cgi.removeAllEventStream();

			final Random r = new Random();

			// int i = 0;
			// for(final Coordinate coor: sensorsStreams){
			// cgi.registerEventStream(UUID.randomUUID(),
			// routingKeys.get(i % 10), coor.getLongitude(),
			// coor.getLatitude());
			// i++;
			// }
			final List<Integer> flag = new ArrayList<Integer>();
			for(final String str:routingKeys){
				int m;
				do {
					m = r.nextInt(sensorsStreams.size());
				} while (flag.contains(m));
				flag.add(m);

				final Coordinate coor = sensorsStreams.get(m);
				cgi.registerEventStream(UUID.randomUUID(), str,
						coor.getLongitude(), coor.getLatitude());
				System.out.println(str + "---" + coor.toString());
			}

			System.out.println("Done registration event streams!");
		} catch (ClassNotFoundException | SQLException e) {
			e.printStackTrace();
		}
	}

	public static List<String[]> getEventStream(Route route) {

		final List<String[]> exchangeTopics = new ArrayList<String[]>();
		final List<Coordinate> eventCoors = new ArrayList<Coordinate>();
		CpGdiInterface cgi;
		try {
			cgi = new CpGdiInterface("127.0.0.1", 5432);

			final String WKTString = fromCoordinatesToWKT(route);

			CpGdiPersistable[] events;
			System.out.println("Event_Streams on Route:");
			events = cgi.getEventStreamsForRoute(WKTString, 500);
			System.out.println(events.length);
			for (final CpGdiPersistable cpgdie : events) {
				// System.out.println(cpgdie);
				exchangeTopics.add(new String[] { "events",
						cpgdie.getPropertyDescriptionOrTopic()});
				final PGpoint point = cpgdie.getCenttralPoint();
				// System.out.println(point);
				eventCoors.add(new Coordinate(point.x, point.y));
				System.out.println(cpgdie.getPropertyDescriptionOrTopic()
						+ "---" + new Coordinate(point.x, point.y).toString());
			}

			publishEvent(exchangeTopics, eventCoors);
		} catch (ClassNotFoundException | SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return exchangeTopics;
	}

	/**
	 * This function transform route from List<Coordinates> to WKT string
	 *
	 * @param route
	 * @return
	 */
	public static String fromCoordinatesToWKT(Place place) {
		List<Coordinate> route = new ArrayList<Coordinate>();
		if (place.getPlaceType().equals(PlaceType.POINT)) {
			route.add(((Point) place).getPoint());
		} else if (place.getPlaceType().equals(PlaceType.AREA)) {
			route = ((Area) place).getArea();
		} else {
			route = ((Route) place).getRoute();
		}

		final StringBuilder WKTStr = new StringBuilder();
		if (route.isEmpty()) {
			return WKTStr.toString();
		}
		WKTStr.append("LINESTRING(").append(route.get(0).toString());
		for (int i = 1; i < route.size(); i++) {
			WKTStr.append(",");
			WKTStr.append(route.get(i).toString());
		}
		WKTStr.append(")");

		return WKTStr.toString();
	}

	public static Map<Coordinate, Integer> mapEventCoordinateWithRoute(
			List<Coordinate> eventCoors, Route route) {
		final Map<Coordinate, Integer> map = new HashMap<Coordinate, Integer>();
		for (int i = 0; i < eventCoors.size(); i++) {
			final Coordinate eventCoor = eventCoors.get(i);
			double minDistance = eventCoor.distance(
					route.getRoute().get(0));
			int minIndex = 0;
			for (int j = 1; j < route.getRoute().size(); j++) {
				final double dis = eventCoor.distance(route.getRoute().get(j));
				if (minDistance > dis) {
					minDistance = dis;
					minIndex = j;
				}
			}
			System.out.println("MinDis = " + minDistance);
			System.out.println(i + "---" + minIndex);
			map.put(eventCoor, minIndex);
		}
		return map;
	}

	public static String generateRDFEvent(String eventName, String eventType,
			Coordinate coor) {

		final String ec = "http://purl.oclc.org/NET/UNIS/sao/ec#";
		final String sao = "http://purl.oclc.org/NET/UNIS/sao/sao#";
		final String xsn = "http://www.w3.org/2001/XMLSchema#";
		final String geo = "http://www.w3.org/2003/01/geo/wgs84_pos#";
		final String tl = "http://purl.org/NET/c4dm/timeline.owl#";
		final String prov = "http://www.w3.org/ns/prov#";

		final Model model = ModelFactory.createDefaultModel();

		/*
		 * prefix
		 */
		model.setNsPrefix("ec", ec);
		model.setNsPrefix("xsd", xsn);
		model.setNsPrefix("sao", sao);
		model.setNsPrefix("geo", geo);
		model.setNsPrefix("prov", prov);
		model.setNsPrefix("tl", tl);

		/*
		 * Resource
		 */
		final Resource TransportationEvent = model.createResource(ec
				+ eventType);
		final Resource TrafficJam = model.createResource(ec + eventName);

		final Resource location = model.createResource();
		location.addProperty(RDF.type, geo + "Instant");
		location.addLiteral(model.createProperty(geo + "lat"),
				coor.getLatitude()).addLiteral(
				model.createProperty(geo + "lon"), coor.getLongitude());

		/*
		 * Property
		 */
		final Property hasLocation = model.createProperty(sao + "hasLocation");
		final Property hasType = model.createProperty(sao + "hasType");

		final SimpleDateFormat sdf = new SimpleDateFormat(
				"yyyy-MM-dd'T'HH:mm:ss");

		final Calendar datec = new GregorianCalendar(2007, 3, 4);
		datec.setTimeZone(TimeZone.getTimeZone("GMT+0"));
		final XSDDateTime xsdDate = new XSDDateTime(datec);
		System.out.println(xsdDate.toString());
		final Resource cityEvent = model
				.createResource(sao + "cityEvent_TrafficJam_Id1")
				.addProperty(RDF.type, TrafficJam)
				.addLiteral(
						model.createProperty(tl + "time"),
						new XSDDateTime(DatatypeConverter.parseDateTime(xsdDate
								.toString())))
				// Please double check how to add value of time here !!!!
				.addLiteral(model.createProperty(sao + "hasLevel"), 3)
				.addLiteral(model.createProperty(ec + "hasSource"), "Sensor");

		model.add(cityEvent, hasLocation, location);
		model.add(cityEvent, hasType, TransportationEvent);

		final StringWriter out = new StringWriter();
		model.write(out, "N3");
		final String result = out.toString();
		System.out.println(result + "\n ======================== \n");

		return result;
	}
}
