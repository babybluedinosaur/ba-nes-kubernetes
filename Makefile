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
	kubectl apply -f src/main/resources/physical-source/server.yaml

apply-server-service:
	kubectl apply -f src/main/resources/svc/svc-server.yaml

apply-config:
	 kubectl apply -f src/main/resources/config/topology-config.yaml

apply-nebuli:
	 kubectl create configmap topology-config --from-file=src/main/resources/cr/convert-target.yaml --dry-run=client -o yaml | kubectl apply -f -
	 kubectl apply -f src/main/resources/cr/nebuli.yaml

apply-nebuli-queries:
	kubectl apply -f src/main/resources/cr/nebuli-queries.yaml

delete-cr-topology:
	kubectl delete -f src/main/resources/cr/cr-topology.yaml

delete-crd-topology:
	kubectl delete -f src/main/resources/crd/nes-topology.yaml

delete-nebuli:
	kubectl delete job nebuli

delete-deployments:
	kubectl delete deployment --all

delete-pods:
	kubectl delete pod --all

delete-pvcs:
	kubectl delete pvc --all

delete-queries:
	kubectl delete deployment query-1

delete-server:
	kubectl delete -f src/main/resources/cr/server.yaml

delete-services:
	kubectl delete svc -l topology=nes

delete-configmap:
	kubectl delete configmap -l topology=nes

delete-all:
	make delete-deployments
	make delete-pods
	make delete-services
	make delete-configmap
	make delete-pvcs

describe-deployments:
	kubectl describe deployments

describe-services:
	kubectl describe services

describe-pods:
	kubectl describe pods

describe-crds:
	kubectl describe crd

describe-cr:
	kubectl describe nes-topology test-topology

get-deployments:
	kubectl get deployments

get-services:
	kubectl get services

get-pods:
	kubectl get pods -o wide

get-configmap:
	kubectl get configmap

get-pvcs:
	kubectl get pvc

get-pv:
	kubectl get pv

get-crds:
	kubectl get crd

pod-yaml:
	kubectl get pod $(pod) -o yaml > $(pod).yaml

enter-reader:
	kubectl exec -it $(reader) -- sh

fresh-node:
	make delete-deployments
	make delete-pods
	make apply-server
	make apply-server-service
	make run

copy-nebuli-queries:
	kubectl cp nebuli-queries-reader:/tmp ./


logs:
	kubectl logs deployment/test-topology

logs-nebuli:
	kubectl	logs nebuli

watch-deployments:
	watch kubectl get deployments

watch-pods:
	watch kubectl get pods -o wide

random-pod:
	kubectl run -it --rm --image=busybox debug -- sh

# nc tcp-server-service 6666
# apt-get update && apt-get install -y netcat-openbsd

docker-push:
	docker build --no-cache -t sidondocker/sido-nebuli .
	docker push sidondocker/sido-nebuli