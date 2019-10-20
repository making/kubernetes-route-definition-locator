package am.ik.cloud.gateway.locator;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;

import am.ik.cloud.gateway.crd.DoneableRouteDefinition;
import am.ik.cloud.gateway.crd.RouteDefinitionList;
import am.ik.cloud.gateway.crd.RouteDefinitionSpec;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import reactor.core.publisher.Flux;

public class KubernetesRouteDefinitionLocator implements RouteDefinitionLocator,
		Watcher<am.ik.cloud.gateway.crd.RouteDefinition> {
	private static final Logger log = LoggerFactory
			.getLogger(KubernetesRouteDefinitionLocator.class);
	private final ConcurrentMap<String, RouteDefinition> routeDefinitions = new ConcurrentHashMap<>();
	private final KubernetesClient kubernetesClient;
	private final ApplicationEventPublisher eventPublisher;
	private final CustomResourceDefinition crd;

	public KubernetesRouteDefinitionLocator(KubernetesClient kubernetesClient,
			ApplicationEventPublisher eventPublisher) {
		this.kubernetesClient = kubernetesClient;
		this.eventPublisher = eventPublisher;
		this.crd = this.kubernetesClient.customResourceDefinitions()
				.withName("routedefinitions.gateway.cloud.ik.am").get();
	}

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return Flux.fromIterable(this.routeDefinitions.values());
	}

	private FilterWatchListMultiDeletable<am.ik.cloud.gateway.crd.RouteDefinition, RouteDefinitionList, Boolean, Watch, Watcher<am.ik.cloud.gateway.crd.RouteDefinition>> routeDefinitionResources() {
		return this.kubernetesClient
				.customResources(this.crd, am.ik.cloud.gateway.crd.RouteDefinition.class,
						RouteDefinitionList.class, DoneableRouteDefinition.class)
				.inAnyNamespace();
	}

	@PostConstruct
	public void watch() {
		this.routeDefinitionResources().watch(this);
	}

	@Override
	public synchronized void eventReceived(Action action,
			am.ik.cloud.gateway.crd.RouteDefinition resource) {
		final ObjectMeta metadata = resource.getMetadata();
		final String id = routeId(metadata);
		if (action == Action.ADDED || action == Action.MODIFIED) {
			final RouteDefinitionSpec spec = resource.getSpec();
			final String name = metadata.getName();
			final String namespace = metadata.getNamespace();
			final Optional<RouteDefinition> rd = this.getRouteDefinition(id, name,
					namespace, spec);
			if (rd.isPresent()) {
				final RouteDefinition routeDefinition = rd.get();
				log.info("Update {}\t{}", id, routeDefinition);
				this.routeDefinitions.put(id, routeDefinition);
				this.eventPublisher.publishEvent(new RefreshRoutesEvent(this));
			}
			else {
				log.warn("Failed to update {}", id);
			}
		}
		else if (action == Action.DELETED) {
			log.info("Delete {}", id);
			this.routeDefinitions.remove(id);
			this.eventPublisher.publishEvent(new RefreshRoutesEvent(this));
		}
	}

	Optional<RouteDefinition> getRouteDefinition(String id, String name, String namespace,
			RouteDefinitionSpec spec) {
		return spec.toRouteDefinition(id, name, namespace,
				(n, ns) -> kubernetesClient.services().inNamespace(ns).withName(n).get());
	}

	@Scheduled(fixedRate = 30_000, initialDelay = 15_000)
	public synchronized void reconcile() {
		log.debug("Reconcile");
		this.routeDefinitionResources().list().getItems().forEach(resource -> {
			final ObjectMeta metadata = resource.getMetadata();
			final String id = routeId(metadata);
			final RouteDefinitionSpec spec = resource.getSpec();
			final String name = metadata.getName();
			final String namespace = metadata.getNamespace();
			final Optional<RouteDefinition> rd = this.getRouteDefinition(id, name,
					namespace, spec);
			rd.ifPresent(it -> this.routeDefinitions.put(id, it));
		});
		this.eventPublisher.publishEvent(new RefreshRoutesEvent(this));
	}

	@Override
	public void onClose(KubernetesClientException e) {
		log.debug("onClose");
	}

	private static String routeId(ObjectMeta metadata) {
		return metadata.getNamespace() + "/" + metadata.getName();
	}
}
