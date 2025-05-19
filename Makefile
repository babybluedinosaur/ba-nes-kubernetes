run:
	mvn compile exec:java -Dexec.mainClass=org.acme.Runner

update-replicas:
	./edit_replicas.sh $(r)
	mvn compile exec:java -Dexec.mainClass=org.acme.Runner

apply-cr-topology:
	#kubectl apply -f src/main/resources/cr/cr-topology.yaml
	kubectl apply -f src/main/resources/cr/convert-source.yaml

apply-crd-topology:
	kubectl apply -f src/main/resources/crds/crd-topology.yaml

apply-server:
	kubectl apply -f src/main/resources/crs/server.yaml

apply-server-service:
	kubectl apply -f src/main/resources/svc/svc-server.yaml

apply-config:
	 kubectl apply -f src/main/resources/config/topology-config.yaml

apply-nebuli:
	 kubectl create configmap topology-config --from-file=src/main/resources/cr/convert-target.yaml --dry-run=client -o yaml | kubectl apply -f -
	 kubectl apply -f src/main/resources/cr/nebuli.yaml

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

logs-nebuli:
	kubectl	logs nebuli

watch-deployments:
	watch kubectl get deployments

watch-pods:
	watch kubectl get pods -o wide