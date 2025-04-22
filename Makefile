run:
	mvn compile exec:java -Dexec.mainClass=org.acme.Runner

update-replicas:
	./edit_replicas.sh $(r)
	mvn compile exec:java -Dexec.mainClass=org.acme.Runner

apply-example:
	kubectl apply -f src/main/resources/crds/nt-example.yaml

apply-crd:
	kubectl apply -f src/main/resources/crds/nes-topology.yaml

delete-example:
	kubectl delete -f src/main/resources/crds/nt-example.yaml

delete-crd:
	kubectl delete -f src/main/resources/crds/nes-topology.yaml

delete-deployment:
	kubectl delete deployment test-topology

describe-deployments:
	kubectl describe deployments

describe-pods:
	kubectl describe pods

describe-cr:
	kubectl describe nes-topology test-topology

get-deployments:
	kubectl get deployments

get-crds:
	kubectl get crds

get-pods:
	kubectl get pods -o wide

logs:
	kubectl logs deployment/test-topology

watch-deployments:
	watch kubectl get deployments

watch-pods:
	watch kubectl get pods -o wide