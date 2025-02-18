/**
 *
 */
package org.insight_centre.urq.citypulse.wp5.contextual_filter.contextual_event_system;

import it.unical.mat.embasp.base.ASPHandler;
import it.unical.mat.embasp.base.AnswerSet;
import it.unical.mat.embasp.base.AnswerSets;
import it.unical.mat.embasp.clingo.ClingoHandler;
import it.unical.mat.embasp.mapper.IllegalTermException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.websocket.Session;

import org.insight_centre.urq.citypulse.wp5.Configuration;
import org.postgresql.geometric.PGpoint;

import citypulse.commons.contextual_filtering.city_event_ontology.CityEvent;
import citypulse.commons.contextual_filtering.city_event_ontology.CriticalEventResults;
import citypulse.commons.contextual_filtering.contextual_event_request.Area;
import citypulse.commons.contextual_filtering.contextual_event_request.ContextualEventRequest;
import citypulse.commons.contextual_filtering.contextual_event_request.Place;
import citypulse.commons.contextual_filtering.contextual_event_request.PlaceAdapter;
import citypulse.commons.contextual_filtering.contextual_event_request.PlaceType;
import citypulse.commons.contextual_filtering.contextual_event_request.Point;
import citypulse.commons.contextual_filtering.contextual_event_request.RankingElement;
import citypulse.commons.contextual_filtering.contextual_event_request.RankingElementName;
import citypulse.commons.contextual_filtering.contextual_event_request.RankingFactor;
import citypulse.commons.contextual_filtering.contextual_event_request.Route;
import citypulse.commons.contextual_filtering.user_context_ontology.UserContext;
import citypulse.commons.data.Coordinate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.citypulse.uaso.gdiclient.CpGdiInterface;
import eu.citypulse.uaso.gdiclient.persistdata.CpGdiPersistable;

/**
 * @author Thu-Le Pham
 * @date 1 Oct 2015
 */
public class ContextualEventFilter {

	public static Logger logger = Logger.getLogger(ContextualEventFilter.class
			.getPackage().getName());

	/**
	 *
	 */
	ContextualEventRequest ceRequest;

	/**
	 *
	 */
	UserContext userContext = new UserContext();

	/**
	 *
	 */
	List<String> inputProgram = new ArrayList<String>();

	/**
	 *
	 */
	Session session;

	/**
	 *
	 */
	int[] scale = { 1, 5 };

	/**
	 * if Place = Route, this object save the mapping between event coordinate &
	 * route coordinate, else it is null String: event coordinate as a string
	 */
	Map<String, Integer> mapEventRoute = new HashMap<String, Integer>();
	/**
	 * if Place = Route, this object save the index of user coordinate on the
	 * route
	 */
	public int userCoordinateIndex = 0;

	/**
	 *
	 */
	CityEventConsumer cityEventConsumer;

	/**
	 *
	 */
	public ContextualEventFilter() {
		super();
	}

	public ContextualEventFilter(Session session) {
		super();
		this.session = session;
	}

	public UserContext getUserContext() {
		return userContext;
	}

	public void setUserContext(UserContext userContext) {
		this.userContext = userContext;
	}

	public List<String> getInputProgram() {
		return inputProgram;
	}

	public void setInputProgram(List<String> inputProgram) {
		this.inputProgram = inputProgram;
	}

	public ContextualEventRequest getCeRequest() {
		return ceRequest;
	}

	public void setCeRequest(ContextualEventRequest ceRequest) {
		this.ceRequest = ceRequest;
	}

	/**
	 *
	 * @param contextualEventRequest
	 * @return
	 */
	public void startCEF(
			ContextualEventRequest contextualEventRequest) {
		logger.info("Start ContextualEventFiltering....");

		this.ceRequest = contextualEventRequest;
		final Place place = contextualEventRequest.getPlace();

		/*
		 * initial UserCoordinate
		 */
		this.userContext.getActivity().setActivityPlace(
				new Point(place.getCentreCoordinate()));

		/*
		 * rewrite request to rules
		 */
		this.inputProgram.addAll(ContextualEventRequestRewriter.getInstance()
				.getRules(contextualEventRequest));

		/*
		 * subcribe events
		 */
		subscribeCityEvents(place);
	}

	/**
	 * This function maps event's coordinate to route's coordinate
	 *
	 * @param eventCoors
	 * @param route
	 * @return
	 */
	public static Map<String, Integer> mapEventCoordinateWithRoute(
			List<Coordinate> eventCoors, Route route) {
		final Map<String, Integer> map = new HashMap<String, Integer>();
		for (int i = 0; i < eventCoors.size(); i++) {
			final Coordinate eventCoor = eventCoors.get(i);
			double minDistance = eventCoor.distance(route.getRoute().get(0));
			int minIndex = 0;
			for (int j = 1; j < route.getRoute().size(); j++) {
				final double dis = eventCoor.distance(route.getRoute().get(j));
				if (minDistance > dis) {
					minDistance = dis;
					minIndex = j;
				}
			}
			System.out.println(eventCoor.toString() + "---" + minIndex
					+ " --- " + minDistance);
			map.put(eventCoor.toString(), minIndex);
		}
		return map;
	}


	/**
	 * This function connects to GeoDB in order to get information for
	 * subscribing events from MessageBus
	 *
	 * @param place
	 *            (of interest)
	 * @return list of coordinates of events that are in the place of interest
	 */
	public void subscribeCityEvents(Place place) {

		/*
		 * Connect to GDI to get information of event streams on place
		 */
		final List<String[]> exchangeTopics = new ArrayList<String[]>();
		final List<Coordinate> eventCoors = new ArrayList<Coordinate>();

		CpGdiInterface cgi;
		try {
			/*
			 * Change "127.0.0.1" to "localhost" when runing on VM
			 */
			// cgi = new CpGdiInterface("127.0.0.1", 5432);
			cgi = new CpGdiInterface("localhost", 5432);

			final String WKTString = fromCoordinatesToWKT(place);
			CpGdiPersistable[] events;
			System.out.println("Event_Streams on Route:");

			// get all event streams around the place with distance 500m
			events = cgi.getEventStreamsForRoute(WKTString, 500);
			System.out.println(events.length);

			for (final CpGdiPersistable cpgdie : events) {
				System.out.println(cpgdie);
				exchangeTopics.add(new String[] { "events",
						cpgdie.getPropertyDescriptionOrTopic() });
				final PGpoint point = cpgdie.getCenttralPoint();
				System.out.println(point);
				eventCoors.add(new Coordinate(point.x, point.y));
				System.out.println(cpgdie.getPropertyDescriptionOrTopic()
						+ "---" + new Coordinate(point.x, point.y).toString());
			}

			/*
			 * If Place of Interest is Route --> map event coordinates to Route
			 * coordinates
			 */
			if (place.getPlaceType().equals(PlaceType.ROUTE)) {
				System.out
						.println("Mapping event coordinate with route coordinate");
				this.mapEventRoute = mapEventCoordinateWithRoute(eventCoors,
						(Route) place);
				System.out.println("MmapEventRoute = " + this.mapEventRoute);
			}

			cgi.closeConnection();
		} catch (ClassNotFoundException | SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		/*
		 * start the CityEventConsumer
		 */
		this.cityEventConsumer = new CityEventConsumer(
				exchangeTopics, this);
	}

	/**
	 * This function transform route from List<Coordinates> to WKT string
	 *
	 * @param route
	 * @return
	 */
	public String fromCoordinatesToWKT(Place place) {
		List<Coordinate> route = new ArrayList<Coordinate>();
		final StringBuilder WKTStr = new StringBuilder();

		if (place.getPlaceType().equals(PlaceType.POINT)) {
			route.add(((Point) place).getPoint());
			WKTStr.append("POINT(");
		}else if(place.getPlaceType().equals(PlaceType.AREA)){
			route = ((Area) place).getArea();
			WKTStr.append("LINESTRING(");
		} else {
			route = ((Route) place).getRoute();
			WKTStr.append("LINESTRING(");
		}
		if (route.isEmpty()) {
			return WKTStr.toString();
		}
		WKTStr.append(route.get(0).toString());
		for (int i = 1; i < route.size(); i++) {
			WKTStr.append(",");
			WKTStr.append(route.get(i).toString());
		}
		WKTStr.append(")");
		return WKTStr.toString();
	}

	/**
	 * @param cityEvents
	 */
	public void performReasoning(List<CityEvent> cityEvents) {

		final List<String> rankingData = createRankingElements(this.ceRequest,
				cityEvents, this.userContext);

		final ASPHandler handler = new ClingoHandler();
		handler.addOption("-n 0");
		handler.addOption("--opt-mode=optN");

		try {
			handler.addFileInput(Configuration.getInstance()
					.getCFResourceFolderPath() + "rules.lp");

		} catch (final FileNotFoundException e) {
			logger.severe(e.toString());
		}


		for (final String rule : this.inputProgram) {
			System.out.println("inputProgram = " + rule);
			handler.addRawInput(rule);
		}

		for (final String rule : rankingData) {
			System.out.println("rankingData = " + rule);
			handler.addRawInput(rule);
		}

		try {
			for (final CityEvent event : cityEvents) {
				handler.addInput(event);
			}
		} catch (IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException
				| SecurityException | IllegalTermException e1) {
			logger.severe(e1.toString());
		}

		handler.addFilter(CityEvent.class);

		AnswerSets answerSets = null;

		try {
			answerSets = handler.reason();
		} catch (final IOException | InterruptedException e) {
			logger.severe(e.toString());
		}

		final List<CityEvent> results = new ArrayList<CityEvent>();
		if (answerSets != null) {

			final List<AnswerSet> answerSetList = answerSets
					.getAnswerSetsList();

			logger.info("Number of Answer Sets: " + answerSetList.size());

			if (!answerSetList.isEmpty()) {
				for (final AnswerSet answerSet : answerSetList) {

					logger.info(answerSet.getAnswerSet().toString());
					final CityEvent criticalEvent = getCriticalEvent(answerSet,
							cityEvents);
					results.add(criticalEvent);
					// logger.info("After Filtering: " +
					// criticalEvent.toString());

					// sendCriticalEvent(criticalEvent);

//					/*
//					 * FIX: convert from CityEvent to ContextualEvent
//					 */
//
//					final ContextualEvent ceEvent = new ContextualEvent();
//					ceEvent.setCeID(criticalEvent.getEventId());
//					ceEvent.setCeName(criticalEvent.getEventCategory()
//							.getCategoryName());
//					ceEvent.setCeType(criticalEvent.getEventType()
//							.getTypeName());
//					ceEvent.setCeLevel(criticalEvent.getEventLevel());
//					ceEvent.setCeCoordinate(criticalEvent.getEventPlace()
//							.getCentreCoordinate());
//
//					if (this.session != null) {
//						final GsonBuilder builder = new GsonBuilder();
//						builder.registerTypeAdapter(Place.class,
//								new PlaceAdapter());
//						final Gson gson = builder.create();
//
//						final String gsonStr = new Gson().toJson(ceEvent);
//						this.session.getAsyncRemote().sendText(gsonStr);
					//
					// } else {
					// logger.info("No Connection with Application!");
					// }
				}
			}
			final CriticalEventResults ceResults = new CriticalEventResults(
					results);
			logger.info("After Filtering: " + ceResults.toString());

			sendCriticalEvent(ceResults);
		}
	}

	/**
	 * @param ceResults
	 */
	private void sendCriticalEvent(CriticalEventResults ceResults) {
		if (this.session != null) {
			final GsonBuilder builder = new GsonBuilder();
			builder.registerTypeAdapter(Place.class, new PlaceAdapter());
			final Gson gson = builder.create();

			final String gsonStr = gson.toJson(ceResults);
			this.session.getAsyncRemote().sendText(gsonStr);

		} else {
			logger.info("No Connection with Application!");
		}
	}

	/**
	 * @param criticalEvent
	 */
	private void sendCriticalEvent(CityEvent criticalEvent) {
		if (this.session != null) {
			final GsonBuilder builder = new GsonBuilder();
			builder.registerTypeAdapter(Place.class, new PlaceAdapter());
			final Gson gson = builder.create();

			final String gsonStr = gson.toJson(criticalEvent);
			this.session.getAsyncRemote().sendText(gsonStr);

		} else {
			logger.info("No Connection with Application!");
		}

	}

	/**
	 * @param answerSet
	 * @return
	 */
	private CityEvent getCriticalEvent(final AnswerSet answerSet,
			List<CityEvent> cityEvents) {

		CityEvent criticalEvent = new CityEvent();

		for (final String str : answerSet.getAnswerSet()) {
			final String eventId = str.split("\"")[1];
			final int criticality = Integer.parseInt(str.substring(
					str.indexOf(",") + 1, str.indexOf(")")).trim());
			for (int i = 0; i < cityEvents.size(); i++) {
				final CityEvent event = cityEvents.get(i);
				if (event.getEventId().equals(eventId)) {
					criticalEvent = event;
					criticalEvent.setContextualCriticality(criticality);
					cityEvents.remove(i);
					break;
				}
			}
		}
		return criticalEvent;
	}

	/**
	 * @param ceRequest
	 * @param cityEvents
	 * @param userContext
	 */
	private List<String> createRankingElements(
			ContextualEventRequest ceRequest,
			List<CityEvent> cityEvents, UserContext userContext) {

		final List<String> rules = new LinkedList<String>();

		final RankingFactor rankingFactor = ceRequest.getRankingFactor();

		final Map<String, Integer> eventDistance = new HashMap<String, Integer>();
		final Map<String, Integer> eventLevel = new HashMap<String, Integer>();

		for (final RankingElement e : rankingFactor.getRankingElements()) {
			if (e.getName().equals(RankingElementName.DISTANCE)) {
				/*
				 * find the distance between event and user context
				 */
				if (!ceRequest.getPlace().getPlaceType()
						.equals(PlaceType.ROUTE)) {
					/*
					 * Place = Point or Area
					 */
					for (final CityEvent event : cityEvents) {
						final long distance = (long) event
								.getEventPlace()
								.getCentreCoordinate()
								.distance(
										userContext.getActivity()
												.getActivityPlace()
												.getCentreCoordinate());

						/*
						 * TODO: Find the min and max value of distance
						 */
						long maxDistance;
						if (ceRequest.getPlace().getPlaceType()
								.equals(PlaceType.ROUTE)) {
							maxDistance = ((Route) ceRequest.getPlace())
									.getLength();
						} else {
							maxDistance = 500;
						}
						int distanceScale = scaling(maxDistance - distance,
								new long[] { 0, maxDistance }, this.scale);
						// TODO: fake data
						if (distanceScale < 0 || distanceScale > 5) {
							distanceScale = Math.abs(distanceScale) % 5;
						}
						eventDistance.put(event.getEventId(), distanceScale);
					}
					rules.addAll(ContextualEventRequestRewriter.getInstance()
							.getRankingEventData(eventDistance,
									e.getName()));
				} else{
					/*
					 * Place = Route
					 */
					final List<String> expiredEvents = new ArrayList<String>();
					final int maxIndex = ((Route) ceRequest.getPlace())
							.getRoute().size();
					for (final CityEvent event : cityEvents) {
						System.out.println("Event coordinate = "
								+ event.getEventId() + "--"
								+ event.getEventPlace().toString() + "--- "
								+ event.getEventPlace().getCentreCoordinate()
										.toString());
						// System.out.println(this.mapEventRoute.keySet());

						if (!this.mapEventRoute.keySet().contains(
								event.getEventPlace().getCentreCoordinate()
										.toString())) {
							System.out
									.println("Expired event since mapEventRoute does not contain event coordinate");
							expiredEvents.add(event.getEventId());
						} else {
							final int eventCoorIndex = this.mapEventRoute
									.get(event.getEventPlace()
											.getCentreCoordinate().toString());
							System.out.println("UserIndex = "
									+ this.userCoordinateIndex
									+ "-- EventIndex = " + eventCoorIndex);
							if (this.userCoordinateIndex > eventCoorIndex) {
								System.out
										.println("Expired event since user passed event");
								expiredEvents.add(event.getEventId());
							} else {
								final int distanceScale = scaling(
										maxIndex
												- (eventCoorIndex - this.userCoordinateIndex),
										new long[] { 0, maxIndex }, this.scale);
								eventDistance.put(event.getEventId(),
										distanceScale);
							}
						}

					}

					rules.addAll(ContextualEventRequestRewriter.getInstance()
							.getRankingEventData( eventDistance,
									e.getName()));
					rules.addAll(ContextualEventRequestRewriter.getInstance()
							.getExpiredEvent(expiredEvents));
				}


			} else if (e.getName().equals(RankingElementName.EVENT_LEVEL)) {
				/*
				 * TODO: get interval of event level
				 */
				final long[] eventLevelInterval = { 1, 3 };
				for (final CityEvent event : cityEvents) {
					final int eventLevelScale = scaling(event.getEventLevel(),
							eventLevelInterval, this.scale);
					eventLevel.put(event.getEventId(), eventLevelScale);
				}
				rules.addAll(ContextualEventRequestRewriter.getInstance()
						.getRankingEventData(eventLevel,
								e.getName()));
			}

		}
		return rules;
	}

	/**
	 * This function maps user's coordinate to route coordinate
	 *
	 * @param userCoordinate
	 * @param route
	 * @param preIndex
	 *            : previous position of user on the route
	 * @return
	 */
	public int mapUserCoordinateWithRoute(Coordinate userCoordinate,
			Route route, int preIndex) {
		/*
		 * Assume that user go forward on the route
		 */
		double minDis = userCoordinate.distance(route.getRoute().get(
				preIndex));
		int minIndex = preIndex;
		for (int i = preIndex; i < route.getRoute().size(); i++) {
			//Just check next 5 coordinate
			if(i <= preIndex + 5){
				final double dis = userCoordinate.distance(route.getRoute()
						.get(i));
				if(minDis > dis){
					minDis = dis;
					minIndex = i;
				}
			} else{
				break;
			}
		}

		return minIndex;
	}

	/**
	 * This function finds distance between event and user location. If User is
	 * in the Route, event coordinate and user coordinate are map to route
	 * coordinate and base on that find the distance
	 *
	 * @param cityEvents
	 *            city events are happening on the route
	 * @param userContext2
	 *            user context
	 * @param map
	 *            map event coordinate and route coordinate index
	 * @return
	 */
	public Map<String, Integer> findEventDistanceForRoute(
			List<CityEvent> cityEvents, UserContext userContext2,
			Map<Coordinate, Integer> map) {
		final Map<String, Integer> eventDistance = new HashMap<String, Integer>();
		for(final CityEvent event: cityEvents){
			final int eventCoorIndex = map.get(event.getEventPlace()
					.getCentreCoordinate());
		}
		return eventDistance;
	}

	/**
	 *
	 * @param x
	 * @param from
	 * @param to
	 * @return
	 */
	public int scaling(long x, long[] from, int[] to) {
		return (int) Math
				.round((to[0] + ((double) (x - from[0]) * (to[1] - to[0]))
				/ (from[1] - from[0])));
	}

	/**
	 * @param userCoor
	 */
	public void receiveUserGPSCoordinate(Coordinate userCoor) {
		/*
		 * update Place of user Activity
		 */
		this.userContext.getActivity().setActivityPlace(new Point(userCoor));

		/*
		 * If Route, find index of user Coordinate on the route
		 */
		if (this.ceRequest.getPlace().getPlaceType().equals(PlaceType.ROUTE)) {
			this.userCoordinateIndex = mapUserCoordinateWithRoute(userCoor,
					(Route) this.ceRequest.getPlace(), this.userCoordinateIndex);
		}
	}

	/**
	 *
	 * @param session
	 */
	public void stopCFF() {
		if (this.cityEventConsumer != null) {
			try {
				this.cityEventConsumer.channel
						.basicCancel(this.cityEventConsumer.consumerTag);
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}

}
