import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

df = pd.read_csv("../results/benchmark_query.csv")
topology_names = df["Query"]
x = np.arange(len(topology_names))

count_firstTimestamp = df[" timeoutsFirstTimestamp"]
count_queryStop = df[" timeoutsQueryStop"]

width = 0.35

figure, axis = plt.subplots(figsize=(10, 6))
axis.bar(x - width/2, count_firstTimestamp, width, capsize=5,
       label="First Timestamp timeouts", color="darkblue")
axis.bar(x + width/2, count_queryStop, width, capsize=5,
       label="Query stop timeouts", color="darkred")

axis.set_ylabel("Number of timeouts")
axis.set_xlabel("Query")
axis.set_xticks(x)
axis.set_xticklabels(topology_names)
axis.legend()

plt.title("Number of Timeouts during benchmarks of first timestamp/query stop")
plt.tight_layout()
plt.savefig("../plots/pngs/plot_query_timeouts.png")
