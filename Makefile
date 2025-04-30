run:
	mvn compile exec:java -Dexec.mainClass=org.acme.Runner

update-replicas:
	./edit_replicas.sh $(r)
	mvn compile exec:java -Dexec.mainClass=org.acme.Runner

apply-example:
	kubectl apply -f src/main/resources/crds/nes-example.yaml

apply-crd:
	kubectl apply -f src/main/resources/crds/nes-topology.yaml

apply-network:
	kubectl apply -f src/main/resources/crds/nes-network.yaml

delete-example:
	kubectl delete -f src/main/resources/crds/nes-example.yaml

delete-crd:
	kubectl delete -f src/main/resources/crds/nes-topology.yaml

delete-deployments:
	kubectl delete deployment --all

describe-deployments:
	kubectl describe deployments

describe-services:
	kubectl describe services

describe-pods:
	kubectl describe pods

describe-cr:
	kubectl describe nes-topology test-topology

describe-network:
	kubectl describe networkpolicy

get-deployments:
	kubectl get deployments

get-crds:
	kubectl get crds

get-services:
	kubectl get services

get-pods:
	kubectl get pods -o wide

get-network:
	kubectl get networkpolicy

logs:
	kubectl logs deployment/test-topology

watch-deployments:
	watch kubectl get deployments

watch-pods:
	watch kubectl get pods -o wide