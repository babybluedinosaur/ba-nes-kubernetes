run:
	mvn compile exec:java -Dexec.mainClass=org.acme.Runner

update-replicas:
	./edit_replicas.sh $(r)
	mvn compile exec:java -Dexec.mainClass=org.acme.Runner

apply-cr-topology:
	#kubectl apply -f src/main/resources/crs/cr-topology.yaml
	kubectl apply -f src/main/resources/crs/convert-source.yaml

apply-crd-topology:
	kubectl apply -f src/main/resources/crds/crd-topology.yaml

delete-cr-topology:
	kubectl delete -f src/main/resources/crs/cr-topology.yaml

delete-crd-topology:
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

get-deployments:
	kubectl get deployments

get-crds:
	kubectl get crds

get-services:
	kubectl get services

get-pods:
	kubectl get pods -o wide

logs:
	kubectl logs deployment/test-topology

watch-deployments:
	watch kubectl get deployments

watch-pods:
	watch kubectl get pods -o wide