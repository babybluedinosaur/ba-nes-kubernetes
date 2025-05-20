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
	kubectl apply -f src/main/resources/cr/server.yaml

apply-server-service:
	kubectl apply -f src/main/resources/svc/svc-server.yaml

apply-config:
	 kubectl apply -f src/main/resources/config/topology-config.yaml

apply-nebuli:
	 kubectl create configmap topology-config --from-file=src/main/resources/cr/convert-target.yaml --dry-run=client -o yaml | kubectl apply -f -
	 kubectl apply -f src/main/resources/cr/nebuli.yaml

delete-cr-topology:
	kubectl delete -f src/main/resources/cr/cr-topology.yaml

delete-crd-topology:
	kubectl delete -f src/main/resources/crd/nes-topology.yaml

delete-nebuli:
	kubectl delete job nebuli

delete-deployments:
	kubectl delete deployment --all

delete-server:
	kubectl delete -f src/main/resources/cr/server.yaml

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

pod-yaml:
	kubectl get pod $(pod) -o yaml > $(pod).yaml

logs:
	kubectl logs deployment/test-topology

logs-nebuli:
	kubectl	logs nebuli

watch-deployments:
	watch kubectl get deployments

watch-pods:
	watch kubectl get pods -o wide

# kubectl run -it --rm --image=busybox debug -- sh
# nc tcp-server-service 6666
# apt-get update && apt-get install -y netcat-openbsd