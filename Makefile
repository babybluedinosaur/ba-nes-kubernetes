run:
	mvn compile exec:java -Dexec.mainClass=org.acme.Runner

benchmark:
	mvn clean test -Dtest=org.acme.BenchmarkQueryTest

run-plots:
	source venv/bin/activate
	python plots/plot_edgeless.py

minikube:
	minikube start --docker-opt="default-ulimit=nofile=1048576:1048576"

metrics-server:
	kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

apply-cr-topology:
	kubectl apply -f src/main/resources/cr/convert-source.yaml

apply-crd-topology:
	kubectl apply -f src/main/resources/crds/crd-topology.yaml

apply-server:
	kubectl apply -f src/main/resources/physical-source/server-pod-1.yaml

apply-server-service:
	kubectl apply -f src/main/resources/svc/svc-server.yaml

apply-config:
	 kubectl apply -f src/main/resources/config/topology-config.yaml

apply-nebuli:
	 kubectl create configmap topology-config --from-file=src/main/resources/cr/convert-target.yaml --dry-run=client -o yaml | kubectl apply -f -
	 kubectl apply -f src/main/resources/cr/nebuli.yaml

apply-queries:
	kubectl apply -f src/main/resources/cr/queries/query.yaml
	kubectl apply -f src/main/resources/cr/queries/query-2.yaml
	kubectl apply -f src/main/resources/cr/queries/query-3.yaml
	kubectl apply -f src/main/resources/cr/queries/query-4.yaml
	kubectl apply -f src/main/resources/cr/queries/query-5.yaml

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
	kubectl delete deployment -l query=nebuli

delete-services:
	kubectl delete svc -l topology=nes

delete-configmap:
	kubectl delete configmap -l topology=nes

delete-topology:
	kubectl delete deployment -l nes=worker

delete-topologies-cr:
	kubectl delete nes-topologies.nebulastream.com --all

delete-queries-cr:
	kubectl delete nes-queries.nebulastream.com --all
	kubectl delete job -l query=nebuli

delete-setup:
	make delete-topology
	make delete-queries
	make delete-server

delete-server:
	kubectl delete -f src/main/resources/physical-source/server.yaml

delete-crs:
	make delete-topologies-cr
	make delete-queries-cr

delete-all:
	make delete-crs
	make delete-deployments
	make delete-queries-cr
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

describe-configmap:
	kubectl describe configmap

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

get-query-cr:
	kubectl get nes-queries.nebulastream.com

get-topology-cr:
	kubectl get nes-topologies.nebulastream.com

pod-yaml:
	kubectl get pod $(pod) -o yaml > $(pod).yaml

enter-reader:
	kubectl exec -it $(reader) -- sh

fresh-node:
	make delete-deployments
	make delete-pods
	make apply-server
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

docker-push:
	docker build --no-cache --pull -t sidondocker/sido-nebuli .
	docker push sidondocker/sido-nebuli

start-ui:
	k9s

# nc tcp-server-service 6666
# apt-get update && apt-get install -y netcat-openbsd