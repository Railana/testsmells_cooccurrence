import pandas as pd

df = pd.read_csv('coocurrence_methods.csv')

unranked = []

for idx,row in df.iterrows():
    for _,ts in df['NAME'].items():
        unranked.append({'ts1': row['NAME'], 'ts2': ts, 'value': row[ts]})

ranking = pd.DataFrame(unranked)
ranking = ranking.sort_values('value', ascending=False)

ranking.to_csv('ranking_methods.csv')
