import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

df = pd.read_csv("../results/benchmark_edgeless.csv")
topology_names = df["Topology"]
x = np.arange(len(topology_names))

deploy_means = df["Deploy duration(ms)"]
delete_means = df["Delete duration(ms)"]
deploy_std = df["Deploy stdEv(ms)"]
delete_std = df["Delete stdEv(ms)"]

width = 0.35

figure, axis = plt.subplots(figsize=(10, 6))
axis.bar(x - width/2, deploy_means, width, yerr=deploy_std, capsize=5,
       label="Deploy", color="skyblue")
axis.bar(x + width/2, delete_means, width, yerr=delete_std, capsize=5,
       label="Delete", color="red")

axis.set_ylabel("Duration(ms)")
axis.set_xlabel("Topology")
axis.set_xticks(x)
axis.set_xticklabels(topology_names)
axis.legend()

plt.title("Duration between applying of CR and readiness/deletion of topology")
plt.tight_layout()
plt.savefig("../plots/pngs/plot_edgeless.png")
