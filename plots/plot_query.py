import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

df = pd.read_csv("../results/benchmark_query.csv")
topology_names = df["Query"]
x = np.arange(len(topology_names))

deploy_means = df["First timestamp duration(ms)"]
delete_means = df["Query stop duration(ms)"]
deploy_std = df["First timestamp duration stdEv(ms)"]
delete_std = df["Query stop stdEv(ms)"]

deploy_yerr = [np.minimum(deploy_means, deploy_std), deploy_std]
delete_yerr = [np.minimum(delete_means, delete_std), delete_std]

width = 0.35

figure, axis = plt.subplots(figsize=(10, 6))
axis.bar(x - width/2, deploy_means, width, yerr=deploy_yerr, capsize=5,
       label="First timestamp", color="green")
axis.bar(x + width/2, delete_means, width, yerr=delete_yerr, capsize=5,
       label="Query unregister", color="lightgreen")

axis.set_ylabel("Duration(ms)")
axis.set_xlabel("Query")
axis.set_xticks(x)
axis.set_xticklabels(topology_names)
axis.legend()

plt.title("Duration between applying of CR and first timestamp/unregistration of Query")
plt.tight_layout()
plt.savefig("../plots/pngs/plot_query.png")
