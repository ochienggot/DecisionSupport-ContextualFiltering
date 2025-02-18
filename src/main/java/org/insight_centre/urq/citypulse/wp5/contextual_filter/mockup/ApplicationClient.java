package org.insight_centre.urq.citypulse.wp5.contextual_filter.mockup;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import citypulse.commons.contextual_filtering.contextual_event_request.ContextualEventRequest;
import citypulse.commons.contextual_filtering.contextual_event_request.Place;
import citypulse.commons.contextual_filtering.contextual_event_request.PlaceAdapter;
import citypulse.commons.contextual_filtering.contextual_event_request.Route;
import citypulse.commons.data.Coordinate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ApplicationClient implements Runnable{

	String request;

	ContextualEventRequest ceRequest;

	public ApplicationClient(String request) {
		super();
		this.request = request;
	}

	public ApplicationClient(ContextualEventRequest ceRequest) {
		super();
		this.ceRequest = ceRequest;
		this.request = this.parse(ceRequest);
	}

	@Override
	public void run() {
		//Mock up User of Open Meeting

		try {
		            // open websocket
			final URI uri = new URI(
					"ws://131.227.92.55:8005/websockets/contextual_events_request");
			final ClientEndPoint clientEndPoint = new ClientEndPoint(uri);
		            // add listener
			clientEndPoint
					.addMessageHandler(new ClientEndPoint.MessageHandler() {
		                @Override
						public void handleMessage(String message) {
							ClientEndPoint.logger
									.info("Aplication received message: "
											+ message);
		                }
		            });

			clientEndPoint.sendMessage(this.request);

			/*
			 * generate user's coordinates
			 */
			// final List<Coordinate> userCoors =
			// generateUserCoordinateRoute((Route) ceRequest
			// .getPlace());
			// for (int i = 0; i < userCoors.size(); i++) {
			// Thread.sleep(500);
			// System.out.println("userCoordinate ---"
			// + userCoors.get(i).toString());
			// final UserGPSCoordinate coor = new UserGPSCoordinate(
			// userCoors.get(i));
			// clientEndPoint.sendMessage(new Gson().toJson(coor));
			// }

			clientEndPoint.getLatch().await();

		} catch (final URISyntaxException ex) {
			System.err.println("URISyntaxException exception: "
					+ ex.getMessage());
		} catch (final InterruptedException e) {
					// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static List<Coordinate> generateUserCoordinateRoute(Route route) {
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

	public static String parse(ContextualEventRequest request) {
		/*
		 * Convert to Gson string
		 */
		final GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(Place.class, new PlaceAdapter());
		final Gson gson = builder.create();

		final String gsonStr = gson.toJson(request);
		System.out.println("Request_gsonStr = " + gsonStr);
		return gsonStr;
	}


}
