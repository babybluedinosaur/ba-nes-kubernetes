run:
	mvn compile exec:java -Dexec.mainClass=org.acme.Runner

benchmark:
	mvn clean test -Dtest=org.acme.BenchmarkQueryTest

benchmark-edgeless:
	mvn clean test -Dtest=org.acme.BenchmarkTest

run-gke:
	make delete-all
	kubectl delete deployment nes-k8s-operator
	kubectl apply -f src/main/resources/operator/operator-deployment.yaml

benchmark-gke:
	make delete-all
	kubectl delete job benchmark-query-test
	make delete-pvcs
	kubectl apply -f src/main/resources/operator/benchmark-rbac.yaml
	kubectl apply -f src/main/resources/operator/operator-deployment.yaml
	kubectl apply -f src/main/resources/operator/benchmark-pvc.yaml
	kubectl apply -f src/main/resources/operator/benchmark-job.yaml

run-plots:
	source venv/bin/activate
	python plots/plot_edgeless.py

minikube:
	minikube start --docker-opt="default-ulimit=nofile=1048576:1048576"

apply-file-test:
	kubectl apply -f src/main/resources/cr/systest-examples/pvc/stream-files-pvc.yaml
	kubectl apply -f src/main/resources/cr/systest-examples/systest-simulator-pod.yaml
	kubectl cp ./src/main/resources/cr/systest-examples/input-files/. systest-simulator-pod:/data
	kubectl apply -f src/main/resources/cr/systest-examples/examples/topologies/file-1.yaml

apply-server-test:
	kubectl apply -f src/main/resources/cr/systest-examples/pvc/stream-files-pvc.yaml
	kubectl apply -f src/main/resources/physical-source/server.yaml
	kubectl apply -f src/main/resources/cr/topologies/edge/star/star-1.yaml
	kubectl apply -f src/main/resources/cr/systest-examples/examples/queries/query-file-1.yaml

delete-cr-topology:
	kubectl delete -f src/main/resources/cr/cr-topology.yaml

delete-crd-topology:
	kubectl delete -f src/main/resources/crd/nes-topology.yaml

delete-nebuli:
	kubectl delete job nebuli

delete-deployments:
	kubectl delete deployment -l nes=worker
	kubectl delete deployment -l app=nes-operator

delete-pods:
	kubectl delete pod -l nes=server

delete-jobs:
	kubectl delete job -l query=nebuli

delete-pvcs:
	kubectl delete pv --all
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
	make delete-jobs
	make delete-pods
	make delete-services
	make delete-configmap
	kubectl delete pod systest-simulator-pod
	kubectl delete deployment nes-k8s-operator
	kubectl delete job --all

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

docker-push-nebuli:
	docker build --no-cache --pull -t sidondocker/sido-nebuli -f Dockerfile.nebuli .
	docker push sidondocker/sido-nebuli

docker-push-operator:
	docker build -t nes-k8s-operator:0.1.0 -f src/main/java/org/acme/Dockerfile .
	docker tag nes-k8s-operator:0.1.0 europe-west3-docker.pkg.dev/iconic-mariner-468912-g1/nes-k8s-operator/nes-k8s-operator:0.1.0
	docker push europe-west3-docker.pkg.dev/iconic-mariner-468912-g1/nes-k8s-operator/nes-k8s-operator:0.1.0

docker-public-operator:
	docker build -t nes-k8s-operator:0.1.0 -f src/main/java/org/acme/Dockerfile .
	docker tag nes-k8s-operator:0.1.0 docker.io/sidondocker/nes-k8s-operator:0.1.0
	docker push docker.io/sidondocker/nes-k8s-operator:0.1.0

docker-push-benchmarkquery:
	docker build -t nes-benchmark:0.1.0 -f src/test/java/org/acme/Dockerfile.query .
	docker tag nes-benchmark:0.1.0 europe-west3-docker.pkg.dev/iconic-mariner-468912-g1/nes-benchmark/nes-benchmark:0.1.0
	docker push europe-west3-docker.pkg.dev/iconic-mariner-468912-g1/nes-benchmark/nes-benchmark:0.1.0

docker-public-benchmarkquery:
	docker build -t nes-benchmark:0.1.0 -f src/test/java/org/acme/Dockerfile.query .
	docker tag nes-benchmark:0.1.0 docker.io/sidondocker/nes-benchmark:0.1.0
	docker push docker.io/sidondocker/nes-benchmark:0.1.0

docker-push-benchmarkedgeless:
	docker build -t nes-benchmark-edgeless:0.1.0 -f src/test/java/org/acme/Dockerfile.edgeless .
	docker tag nes-benchmark-edgeless:0.1.0 europe-west3-docker.pkg.dev/iconic-mariner-468912-g1/nes-benchmark/nes-benchmark-edgeless:0.1.0
	docker push europe-west3-docker.pkg.dev/iconic-mariner-468912-g1/nes-benchmark/nes-benchmark-edgeless:0.1.0

docker-public-benchmarkedgeless:
	docker build -t nes-benchmark-edgeless:0.1.0 -f src/test/java/org/acme/Dockerfile.edgeless .
	docker tag nes-benchmark-edgeless:0.1.0 docker.io/sidondocker/nes-benchmark-edgeless:0.1.0
	docker push docker.io/sidondocker/nes-benchmark-edgeless:0.1.0
get-nodes:
	kubectl get nodes

gck-get-nodepool:
	gcloud container node-pools list --cluster=nes-gke-cluster --zone=europe-west3-a --project=iconic-mariner-468912-g1

gck-get-projects:
	gcloud projects list

gck-get-machines:
	gcloud compute machine-types list --zones=europe-west3-a --project iconic-mariner-468912-g1

gck-get-clusters:
	gcloud container clusters list --project iconic-mariner-468912-g1

gck-delete-pool:
	gcloud container node-pools delete default-pool --cluster=nes-gke-cluster --zone=europe-west3-a --project=iconic-mariner-468912-g1

gck-create-pool:
	gcloud container node-pools create default-pool \
      --cluster nes-gke-cluster \
      --machine-type n2-standard-16 \
      --num-nodes 1 \
      --region europe-west3-a \
      --project iconic-mariner-468912-g1

gck-resize-pool:
	gcloud container clusters resize nes-gke-cluster \
      --num-nodes 1 \
      --region europe-west3-a \
      --project iconic-mariner-468912-g1
switch-context-mini:
	kubectl config use-context minikube

switch-context-gke:
	kubectl config use-context gke_iconic-mariner-468912-g1_europe-west3-a_nes-gke-cluster
start-ui:
	k9s

# nc tcp-server-service 6666
# apt-get update && apt-get install -y netcat-openbsd