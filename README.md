# Kubernetes Route Definition Locator for Spring Cloud Gateway

This project provides a `RouteDefinitionLocator` implementation for Spring Cloud Gateway so that yuo can define `RouteDefinition`s in a Kubernetes native way using CRD.

## How to install `RouteDefinition` CRD

```
kubectl apply -f https://raw.githubusercontent.com/making/kubernetes-route-definition-locator/master/manifest/routedefinition-crd.yml
```

## How to configure

Add the dependency to your Spring Cloud Gateway project:

```xml
<dependency>
    <groupId>am.ik.cloud.gateway</groupId>
    <artifactId>kubernetes-route-definition-locator</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

or you can also get it from [jitpack](https://jitpack.io/#making/kubernetes-route-definition-locator).

[![](https://jitpack.io/v/making/kubernetes-route-definition-locator.svg)](https://jitpack.io/#making/kubernetes-route-definition-locator)

Add a configuration bellow to your Spring Cloud Gateway project:

```java
import am.ik.cloud.gateway.locator.KubernetesRouteDefinitionLocator;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling // <- needed for reconciliation loop
public class KubernetesConfig {

	@Bean
	public KubernetesClient kubernetesClient() {
		Config config = new ConfigBuilder().build();
		return new DefaultKubernetesClient(config);
	}

	@Bean
	public RouteDefinitionLocator kubernetesRouteDefinitionLocator(
			KubernetesClient kubernetesClient, ApplicationEventPublisher eventPublisher) {
		return new KubernetesRouteDefinitionLocator(kubernetesClient, eventPublisher);
	}
}
```

If you are not familiar with Spring Cloud Gateway, start from the following resources:

* https://spring.io/guides/gs/gateway/
* https://content.pivotal.io/engineers/getting-started-with-spring-cloud-gateway-3


Deploy your gateway on your k8s and configure route definitions using CRD.

Your gateway needs a service account that has a role like following:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spring-cloud-gateway
  namespace: your-ns
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: spring-cloud-gateway
rules:
- apiGroups:
  - gateway.cloud.ik.am
  resources:
  - routedefinitions
  verbs:
  - get
  - watch
  - list
- apiGroups:
  - apiextensions.k8s.io
  resources:
  - customresourcedefinitions
  verbs:
  - get
- apiGroups:
  - "" # "" indicates the core API group
  resources:
  - services
  verbs:
  - get
---
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: ClusterRoleBinding
metadata:
  name: gateway-clusterrole-binding
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: spring-cloud-gateway
subjects:
- kind: ServiceAccount
  name: spring-cloud-gateway
  namespace: your-ns
```


## Sample CRDs

```yaml
apiVersion: gateway.cloud.ik.am/v1beta1
kind: RouteDefinition
metadata:
  name: httpbin
spec:
  route:
    uri: https://httpbin.org
    predicates:
    - Path=/get
```

If you want the gateway to route requests to a service in the same k8s cluster, you can configure as follows:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: hello
---
kind: Service
apiVersion: v1
metadata:
  name: hello-pks
  namespace: hello
  labels:
    app: hello-pks
spec:
  selector:
    app: hello-pks
  ports:
  - protocol: TCP
    port: 8080
    name: http
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: hello-pks
  namespace: hello
spec:
  replicas: 1
  selector:
    matchLabels:
      app: hello-pks
  template:
    metadata:
      labels:
        app: hello-pks
    spec:
      containers:
      - image: making/hello-pks:0.0.2
        name: hello-pks
        ports:
        - containerPort: 8080
---
apiVersion: gateway.cloud.ik.am/v1beta1
kind: RouteDefinition
metadata:
  name: hello-pks
  namespace: hello
spec:
  # serviceName: xxxxx (by default, the name of the RouteDefinition is used)
  # portName: xxxxx (specify the port name if the service has multiple ports)
  # scheme: https (by default, http is used)
  route:
    # order: -1 (by default, 0 is used. Lower value is interpreted as higher priority. The same combination of predicates must have different order.)
    predicates:
    - Host=hello-pks.example.com
    filters:
    - PreserveHostHeader=
    - name: Retry
      args:
        retries: 3
        statuses: BAD_GATEWAY
```

## License

Licensed under the Apache License, Version 2.0.