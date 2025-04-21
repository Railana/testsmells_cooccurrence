import pandas as pd
import os
from collections import defaultdict

#accumulo_result_bytestsmells.csv
#asset-share-commons_result_bytestsmells.csv
#maven-dependency-plugin_result_bytestsmells.csv
#bookkeeper_result_bytestsmells.csv
#cassandra_result_bytestsmells.csv
#cayenne_result_bytestsmells.csv
#cxf_result_bytestsmells.csv
#dbeam_result_bytestsmells.csv
#dble_result_bytestsmells.csv
#etcd-java_result_bytestsmells.csv
#facebook-java-business-sdk_result_bytestsmells.csv
#gctoolkit_result_bytestsmells.csv
#guice_result_bytestsmells.csv
#hive_result_bytestsmells.csv
#jcef_result_bytestsmells.csv
#jfnr_result_bytestsmells.csv
#joda-time_result_bytestsmells.csv
#JSqlParser_result_bytestsmells.csv
#kapua_result_bytestsmells.csv
#neptune-export_result_bytestsmells.csv
#wicket_result_bytestsmells.csv
#zookeeper_result_bytestsmells.csv

# Carregar o arquivo CSV com o delimitador ";"

df = pd.read_csv('/Users/railanasantana/Desktop/New_testsmells_analysis/accumulo_result_bytestsmells.csv', delimiter=',')

# Lista de co-ocorrências de test smells a serem analisadas
co_ocorrencias = [
    ("Assertion Roulette", "Conditional Test Logic"),
    ("Assertion Roulette", "Constructor Initialization"),
    ("Assertion Roulette", "Duplicate Assert"),
    ("Assertion Roulette", "Eager Test"),
    ("Assertion Roulette", "EmptyTest"),
    ("Assertion Roulette", "Exception Catching Throwing"),
    ("Assertion Roulette", "General Fixture"),
    ("Assertion Roulette", "IgnoredTest"),
    ("Assertion Roulette", "Lazy Test"),
    ("Assertion Roulette", "Magic Number Test"),
    ("Assertion Roulette", "Mystery Guest"),
    ("Assertion Roulette", "Print Statement"),
    ("Assertion Roulette", "Redundant Assertion"),
    ("Assertion Roulette", "Resource Optimism"),
    ("Assertion Roulette", "Sensitive Equality"),
    ("Assertion Roulette", "Sleepy Test"),
    ("Assertion Roulette", "Unknown Test"),
    ("Assertion Roulette", "Verbose Test"),
    ("Conditional Test Logic", "Constructor Initialization"),
    ("Conditional Test Logic", "Duplicate Assert"),
    ("Conditional Test Logic", "Eager Test"),
    ("Conditional Test Logic", "EmptyTest"),
    ("Conditional Test Logic", "Exception Catching Throwing"),
    ("Conditional Test Logic", "General Fixture"),
    ("Conditional Test Logic", "IgnoredTest"),
    ("Conditional Test Logic", "Lazy Test"),
    ("Conditional Test Logic", "Magic Number Test"),
    ("Conditional Test Logic", "Mystery Guest"),
    ("Conditional Test Logic", "Print Statement"),
    ("Conditional Test Logic", "Redundant Assertion"),
    ("Conditional Test Logic", "Resource Optimism"),
    ("Conditional Test Logic", "Sensitive Equality"),
    ("Conditional Test Logic", "Sleepy Test"),
    ("Conditional Test Logic", "Unknown Test"),
    ("Conditional Test Logic", "Verbose Test"),
    ("Constructor Initialization", "Duplicate Assert"),
    ("Constructor Initialization", "Eager Test"),
    ("Constructor Initialization", "EmptyTest"),
    ("Constructor Initialization", "Exception Catching Throwing"),
    ("Constructor Initialization", "General Fixture"),
    ("Constructor Initialization", "IgnoredTest"),
    ("Constructor Initialization", "Lazy Test"),
    ("Constructor Initialization", "Magic Number Test"),
    ("Constructor Initialization", "Mystery Guest"),
    ("Constructor Initialization", "Print Statement"),
    ("Constructor Initialization", "Redundant Assertion"),
    ("Constructor Initialization", "Resource Optimism"),
    ("Constructor Initialization", "Sensitive Equality"),
    ("Constructor Initialization", "Sleepy Test"),
    ("Constructor Initialization", "Unknown Test"),
    ("Constructor Initialization", "Verbose Test"),
    ("Duplicate Assert", "Eager Test"),
    ("Duplicate Assert", "EmptyTest"),
    ("Duplicate Assert", "Exception Catching Throwing"),
    ("Duplicate Assert", "General Fixture"),
    ("Duplicate Assert", "IgnoredTest"),
    ("Duplicate Assert", "Lazy Test"),
    ("Duplicate Assert", "Magic Number Test"),
    ("Duplicate Assert", "Mystery Guest"),
    ("Duplicate Assert", "Print Statement"),
    ("Duplicate Assert", "Redundant Assertion"),
    ("Duplicate Assert", "Resource Optimism"),
    ("Duplicate Assert", "Sensitive Equality"),
    ("Duplicate Assert", "Sleepy Test"),
    ("Duplicate Assert", "Unknown Test"),
    ("Duplicate Assert", "Verbose Test"),
    ("Eager Test", "EmptyTest"),
    ("Eager Test", "Exception Catching Throwing"),
    ("Eager Test", "General Fixture"),
    ("Eager Test", "IgnoredTest"),
    ("Eager Test", "Lazy Test"),
    ("Eager Test", "Magic Number Test"),
    ("Eager Test", "Mystery Guest"),
    ("Eager Test", "Print Statement"),
    ("Eager Test", "Redundant Assertion"),
    ("Eager Test", "Resource Optimism"),
    ("Eager Test", "Sensitive Equality"),
    ("Eager Test", "Sleepy Test"),
    ("Eager Test", "Unknown Test"),
    ("Eager Test", "Verbose Test"),
    ("EmptyTest", "Exception Catching Throwing"),
    ("EmptyTest", "General Fixture"),
    ("EmptyTest", "IgnoredTest"),
    ("EmptyTest", "Lazy Test"),
    ("EmptyTest", "Magic Number Test"),
    ("EmptyTest", "Mystery Guest"),
    ("EmptyTest", "Print Statement"),
    ("EmptyTest", "Redundant Assertion"),
    ("EmptyTest", "Resource Optimism"),
    ("EmptyTest", "Sensitive Equality"),
    ("EmptyTest", "Sleepy Test"),
    ("EmptyTest", "Unknown Test"),
    ("EmptyTest", "Verbose Test"),
    ("Exception Catching Throwing", "General Fixture"),
    ("Exception Catching Throwing", "IgnoredTest"),
    ("Exception Catching Throwing", "Lazy Test"),
    ("Exception Catching Throwing", "Magic Number Test"),
    ("Exception Catching Throwing", "Mystery Guest"),
    ("Exception Catching Throwing", "Print Statement"),
    ("Exception Catching Throwing", "Redundant Assertion"),
    ("Exception Catching Throwing", "Resource Optimism"),
    ("Exception Catching Throwing", "Sensitive Equality"),
    ("Exception Catching Throwing", "Sleepy Test"),
    ("Exception Catching Throwing", "Unknown Test"),
    ("Exception Catching Throwing", "Verbose Test"),
    ("General Fixture", "IgnoredTest"),
    ("General Fixture", "Lazy Test"),
    ("General Fixture", "Magic Number Test"),
    ("General Fixture", "Mystery Guest"),
    ("General Fixture", "Print Statement"),
    ("General Fixture", "Redundant Assertion"),
    ("General Fixture", "Resource Optimism"),
    ("General Fixture", "Sensitive Equality"),
    ("General Fixture", "Sleepy Test"),
    ("General Fixture", "Unknown Test"),
    ("General Fixture", "Verbose Test"),
    ("IgnoredTest", "Lazy Test"),
    ("IgnoredTest", "Magic Number Test"),
    ("IgnoredTest", "Mystery Guest"),
    ("IgnoredTest", "Print Statement"),
    ("IgnoredTest", "Redundant Assertion"),
    ("IgnoredTest", "Resource Optimism"),
    ("IgnoredTest", "Sensitive Equality"),
    ("IgnoredTest", "Sleepy Test"),
    ("IgnoredTest", "Unknown Test"),
    ("IgnoredTest", "Verbose Test"),
    ("Lazy Test", "Magic Number Test"),
    ("Lazy Test", "Mystery Guest"),
    ("Lazy Test", "Print Statement"),
    ("Lazy Test", "Redundant Assertion"),
    ("Lazy Test", "Resource Optimism"),
    ("Lazy Test", "Sensitive Equality"),
    ("Lazy Test", "Sleepy Test"),
    ("Lazy Test", "Unknown Test"),
    ("Lazy Test", "Verbose Test"),
    ("Magic Number Test", "Mystery Guest"),
    ("Magic Number Test", "Print Statement"),
    ("Magic Number Test", "Redundant Assertion"),
    ("Magic Number Test", "Resource Optimism"),
    ("Magic Number Test", "Sensitive Equality"),
    ("Magic Number Test", "Sleepy Test"),
    ("Magic Number Test", "Unknown Test"),
    ("Magic Number Test", "Verbose Test"),
    ("Mystery Guest", "Print Statement"),
    ("Mystery Guest", "Redundant Assertion"),
    ("Mystery Guest", "Resource Optimism"),
    ("Mystery Guest", "Sensitive Equality"),
    ("Mystery Guest", "Sleepy Test"),
    ("Mystery Guest", "Unknown Test"),
    ("Mystery Guest", "Verbose Test"),
    ("Print Statement", "Redundant Assertion"),
    ("Print Statement", "Resource Optimism"),
    ("Print Statement", "Sensitive Equality"),
    ("Print Statement", "Sleepy Test"),
    ("Print Statement", "Unknown Test"),
    ("Print Statement", "Verbose Test"),
    ("Redundant Assertion", "Resource Optimism"),
    ("Redundant Assertion", "Sensitive Equality"),
    ("Redundant Assertion", "Sleepy Test"),
    ("Redundant Assertion", "Unknown Test"),
    ("Redundant Assertion", "Verbose Test"),
    ("Resource Optimism", "Sensitive Equality"),
    ("Resource Optimism", "Sleepy Test"),
    ("Resource Optimism", "Unknown Test"),
    ("Resource Optimism", "Verbose Test"),
    ("Sensitive Equality", "Sleepy Test"),
    ("Sensitive Equality", "Unknown Test"),
    ("Sensitive Equality", "Verbose Test"),
    ("Sleepy Test", "Unknown Test"),
    ("Sleepy Test", "Verbose Test"),
    ("Unknown Test", "Verbose Test")
]

# Contador para as co-ocorrências, inicializando todas com zero
co_ocorrencias_count = defaultdict(int)

# Agrupar o DataFrame pelos métodos
grouped = df.groupby(['pathFile', 'testSmellMethod'])

# Verificar cada grupo para co-ocorrência de test smells
for _, group in grouped:
    test_smells = set(group['testSmellName'])

    # Iterar sobre cada par de co-ocorrência na lista fornecida
    for smell1, smell2 in co_ocorrencias:
        if smell1 in test_smells and smell2 in test_smells:
            co_ocorrencias_count[(smell1, smell2)] += 1

# Verifica se o arquivo CSV existe e exclui se necessário
output_file = '/Users/railanasantana/Desktop/New_testsmells_analysis/resultado.csv'
if os.path.exists(output_file):
    os.remove(output_file)

# Preparar os dados para o CSV, garantindo que todas co-ocorrências sejam listadas
data = []
for smell1, smell2 in co_ocorrencias:
    count = co_ocorrencias_count[(smell1, smell2)]
    data.append([smell1, smell2, count])

# Criar o DataFrame e ordenar o resultado de A a Z
df_resultado = pd.DataFrame(data, columns=['Primeiro Test Smell', 'Segundo Test Smell', 'Frequência de Métodos'])
df_resultado = df_resultado.sort_values(by=['Primeiro Test Smell', 'Segundo Test Smell'])

# Salvar o resultado no arquivo CSV
df_resultado.to_csv(output_file, index=False, sep=';')

# Exibir mensagem de sucesso
print(f"Resultado salvo no arquivo {output_file}.")
