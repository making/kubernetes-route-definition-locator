package am.ik.cloud.gateway.locator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.gateway.route.RouteDefinition;

import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

public class MapUtils {

	public static List<Tuple3<State, String, RouteDefinition>> difference(Map<String, RouteDefinition> before,
                                                                          Map<String, RouteDefinition> after) {
		final List<Tuple3<State, String, RouteDefinition>> difference = new ArrayList<>();
		after.forEach((k, v) -> {
			if (before.containsKey(k)) {
				final RouteDefinition routeDefinition = before.get(k);
				if (!routeDefinition.equals(v)) {
					difference.add(Tuples.of(State.UPDATED, k, v));
				}
			} else {
				difference.add(Tuples.of(State.ADDED, k, v));
			}
		});
		before.forEach((k, v) -> {
			if (!after.containsKey(k)) {
				difference.add(Tuples.of(State.DELETED, k, v));
			}
		});
		return difference;
	}

	enum State {
		ADDED, UPDATED, DELETED
	}
}
