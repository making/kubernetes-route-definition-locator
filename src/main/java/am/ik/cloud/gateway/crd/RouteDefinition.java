package am.ik.cloud.gateway.crd;

import io.fabric8.kubernetes.client.CustomResource;

import java.util.StringJoiner;

public class RouteDefinition extends CustomResource {
	private RouteDefinitionSpec spec;

	public RouteDefinitionSpec getSpec() {
		return spec;
	}

	public void setSpec(RouteDefinitionSpec spec) {
		this.spec = spec;
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", RouteDefinition.class.getSimpleName() + "[", "]")
				.add("namespace=" + getMetadata().getNamespace()) //
				.add("name=" + getMetadata().getName()) //
				.add("spec=" + spec) //
				.toString();
	}
}
