import csv
import os
from itertools import combinations
from collections import defaultdict

all_files = [
    "01_accumulo_result_bytestsmells.csv",
    "02_apache-maven-dependency-plugin_result_bytestsmells.csv",
    "03_asset-share-commons_result_bytestsmells.csv",
    "04_bookkeeper_result_bytestsmells.csv",
    "05_cassandra_result_bytestsmells.csv",
    "06_cayenne_result_bytestsmells.csv",
    "07_cxf_result_bytestsmells.csv" ,
    "08_dbeam_result_bytestsmells.csv",
    "09_dble_result_bytestsmells.csv",
    "10_etcd-java_result_bytestsmells.csv",
    "11_facebook-java-business-sdk_result_bytestsmells.csv",
    "12_gctoolkit_result_bytestsmells.csv",
    "13_guice_result_bytestsmells.csv",
    "14_hive_result_bytestsmells.csv",
    "15_jcef_result_bytestsmells.csv",
    "16_jfnr_result_bytestsmells.csv",
    "17_joda-time_result_bytestsmells.csv",
    "18_JSqlParser_result_bytestsmells.csv",
    "19_kapua_result_bytestsmells.csv",
    "20_neptune-export_result_bytestsmells.csv",
    "21_wicket_result_bytestsmells.csv",
    "22_zookeeper_result_bytestsmells.csv"
]
def read_csv(file_path):
    test_smells = defaultdict(lambda: defaultdict(set))
    with open(file_path, 'r') as file:
        reader = csv.DictReader(file, delimiter=',')
        for row in reader:
            smell_name = row['testSmellName']
            path_file = row['pathFile']
            test_smells[path_file][smell_name].add(row['testSmellMethod'])
    return test_smells

def count_cooccurrences(test_smells, smell_pairs):
    cooccurrences = defaultdict(int)
    
    for smells_in_class in test_smells.values():
        smell_types = list(smells_in_class.keys())
        for (smell1, smell2) in combinations(smell_types, 2):
            # Ordena os pares de test smells para garantir que (A, B) e (B, A) sejam tratados da mesma forma
            if smell1 < smell2:
                cooccurrences[(smell1, smell2)] += 1
            else:
                cooccurrences[(smell2, smell1)] += 1

    for (smell1, smell2) in smell_pairs:
        if (smell1, smell2) not in cooccurrences and (smell2, smell1) not in cooccurrences:
            cooccurrences[(smell1, smell2)] = 0
    
    return cooccurrences

def save_results(cooccurrences, output_file):
    with open(output_file, 'w', newline='') as file:
        writer = csv.writer(file)
        writer.writerow(['TestSmell1', 'TestSmell2', 'Count'])
        # Ordena os pares de test smells antes de gravar
        for (smell1, smell2) in sorted(cooccurrences):
            writer.writerow([smell1, smell2, cooccurrences[(smell1, smell2)]])

smell_pairs = [
    ('Assertion Roulette', 'Conditional Test Logic'),
    ('Assertion Roulette', 'Constructor Initialization'),
    ('Assertion Roulette', 'Duplicate Assert'),
    ('Assertion Roulette', 'Eager Test'),
    ('Assertion Roulette', 'EmptyTest'),
    ('Assertion Roulette', 'Exception Catching Throwing'),
    ('Assertion Roulette', 'General Fixture'),
    ('Assertion Roulette', 'IgnoredTest'),
    ('Assertion Roulette', 'Lazy Test'),
    ('Assertion Roulette', 'Magic Number Test'),
    ('Assertion Roulette', 'Mystery Guest'),
    ('Assertion Roulette', 'Print Statement'),
    ('Assertion Roulette', 'Redundant Assertion'),
    ('Assertion Roulette', 'Resource Optimism'),
    ('Assertion Roulette', 'Sensitive Equality'),
    ('Assertion Roulette', 'Sleepy Test'),
    ('Assertion Roulette', 'Unknown Test'),
    ('Assertion Roulette', 'Verbose Test'),
    ('Conditional Test Logic', 'Constructor Initialization'),
    ('Conditional Test Logic', 'Duplicate Assert'),
    ('Conditional Test Logic', 'Eager Test'),
    ('Conditional Test Logic', 'EmptyTest'),
    ('Conditional Test Logic', 'Exception Catching Throwing'),
    ('Conditional Test Logic', 'General Fixture'),
    ('Conditional Test Logic', 'IgnoredTest'),
    ('Conditional Test Logic', 'Lazy Test'),
    ('Conditional Test Logic', 'Magic Number Test'),
    ('Conditional Test Logic', 'Mystery Guest'),
    ('Conditional Test Logic', 'Print Statement'),
    ('Conditional Test Logic', 'Redundant Assertion'),
    ('Conditional Test Logic', 'Resource Optimism'),
    ('Conditional Test Logic', 'Sensitive Equality'),
    ('Conditional Test Logic', 'Sleepy Test'),
    ('Conditional Test Logic', 'Unknown Test'),
    ('Conditional Test Logic', 'Verbose Test'),
    ('Constructor Initialization', 'Duplicate Assert'),
    ('Constructor Initialization', 'Eager Test'),
    ('Constructor Initialization', 'EmptyTest'),
    ('Constructor Initialization', 'Exception Catching Throwing'),
    ('Constructor Initialization', 'General Fixture'),
    ('Constructor Initialization', 'IgnoredTest'),
    ('Constructor Initialization', 'Lazy Test'),
    ('Constructor Initialization', 'Magic Number Test'),
    ('Constructor Initialization', 'Mystery Guest'),
    ('Constructor Initialization', 'Print Statement'),
    ('Constructor Initialization', 'Redundant Assertion'),
    ('Constructor Initialization', 'Resource Optimism'),
    ('Constructor Initialization', 'Sensitive Equality'),
    ('Constructor Initialization', 'Sleepy Test'),
    ('Constructor Initialization', 'Unknown Test'),
    ('Constructor Initialization', 'Verbose Test'),
    ('Duplicate Assert', 'Eager Test'),
    ('Duplicate Assert', 'EmptyTest'),
    ('Duplicate Assert', 'Exception Catching Throwing'),
    ('Duplicate Assert', 'General Fixture'),
    ('Duplicate Assert', 'IgnoredTest'),
    ('Duplicate Assert', 'Lazy Test'),
    ('Duplicate Assert', 'Magic Number Test'),
    ('Duplicate Assert', 'Mystery Guest'),
    ('Duplicate Assert', 'Print Statement'),
    ('Duplicate Assert', 'Redundant Assertion'),
    ('Duplicate Assert', 'Resource Optimism'),
    ('Duplicate Assert', 'Sensitive Equality'),
    ('Duplicate Assert', 'Sleepy Test'),
    ('Duplicate Assert', 'Unknown Test'),
    ('Duplicate Assert', 'Verbose Test'),
    ('Eager Test', 'EmptyTest'),
    ('Eager Test', 'Exception Catching Throwing'),
    ('Eager Test', 'General Fixture'),
    ('Eager Test', 'IgnoredTest'),
    ('Eager Test', 'Lazy Test'),
    ('Eager Test', 'Magic Number Test'),
    ('Eager Test', 'Mystery Guest'),
    ('Eager Test', 'Print Statement'),
    ('Eager Test', 'Redundant Assertion'),
    ('Eager Test', 'Resource Optimism'),
    ('Eager Test', 'Sensitive Equality'),
    ('Eager Test', 'Sleepy Test'),
    ('Eager Test', 'Unknown Test'),
    ('Eager Test', 'Verbose Test'),
    ('EmptyTest', 'Exception Catching Throwing'),
    ('EmptyTest', 'General Fixture'),
    ('EmptyTest', 'IgnoredTest'),
    ('EmptyTest', 'Lazy Test'),
    ('EmptyTest', 'Magic Number Test'),
    ('EmptyTest', 'Mystery Guest'),
    ('EmptyTest', 'Print Statement'),
    ('EmptyTest', 'Redundant Assertion'),
    ('EmptyTest', 'Resource Optimism'),
    ('EmptyTest', 'Sensitive Equality'),
    ('EmptyTest', 'Sleepy Test'),
    ('EmptyTest', 'Unknown Test'),
    ('EmptyTest', 'Verbose Test'),
    ('Exception Catching Throwing', 'General Fixture'),
    ('Exception Catching Throwing', 'IgnoredTest'),
    ('Exception Catching Throwing', 'Lazy Test'),
    ('Exception Catching Throwing', 'Magic Number Test'),
    ('Exception Catching Throwing', 'Mystery Guest'),
    ('Exception Catching Throwing', 'Print Statement'),
    ('Exception Catching Throwing', 'Redundant Assertion'),
    ('Exception Catching Throwing', 'Resource Optimism'),
    ('Exception Catching Throwing', 'Sensitive Equality'),
    ('Exception Catching Throwing', 'Sleepy Test'),
    ('Exception Catching Throwing', 'Unknown Test'),
    ('Exception Catching Throwing', 'Verbose Test'),
    ('General Fixture', 'IgnoredTest'),
    ('General Fixture', 'Lazy Test'),
    ('General Fixture', 'Magic Number Test'),
    ('General Fixture', 'Mystery Guest'),
    ('General Fixture', 'Print Statement'),
    ('General Fixture', 'Redundant Assertion'),
    ('General Fixture', 'Resource Optimism'),
    ('General Fixture', 'Sensitive Equality'),
    ('General Fixture', 'Sleepy Test'),
    ('General Fixture', 'Unknown Test'),
    ('General Fixture', 'Verbose Test'),
    ('IgnoredTest', 'Lazy Test'),
    ('IgnoredTest', 'Magic Number Test'),
    ('IgnoredTest', 'Mystery Guest'),
    ('IgnoredTest', 'Print Statement'),
    ('IgnoredTest', 'Redundant Assertion'),
    ('IgnoredTest', 'Resource Optimism'),
    ('IgnoredTest', 'Sensitive Equality'),
    ('IgnoredTest', 'Sleepy Test'),
    ('IgnoredTest', 'Unknown Test'),
    ('IgnoredTest', 'Verbose Test'),
    ('Lazy Test', 'Magic Number Test'),
    ('Lazy Test', 'Mystery Guest'),
    ('Lazy Test', 'Print Statement'),
    ('Lazy Test', 'Redundant Assertion'),
    ('Lazy Test', 'Resource Optimism'),
    ('Lazy Test', 'Sensitive Equality'),
    ('Lazy Test', 'Sleepy Test'),
    ('Lazy Test', 'Unknown Test'),
    ('Lazy Test', 'Verbose Test'),
    ('Magic Number Test', 'Mystery Guest'),
    ('Magic Number Test', 'Print Statement'),
    ('Magic Number Test', 'Redundant Assertion'),
    ('Magic Number Test', 'Resource Optimism'),
    ('Magic Number Test', 'Sensitive Equality'),
    ('Magic Number Test', 'Sleepy Test'),
    ('Magic Number Test', 'Unknown Test'),
    ('Magic Number Test', 'Verbose Test'),
    ('Mystery Guest', 'Print Statement'),
    ('Mystery Guest', 'Redundant Assertion'),
    ('Mystery Guest', 'Resource Optimism'),
    ('Mystery Guest', 'Sensitive Equality'),
    ('Mystery Guest', 'Sleepy Test'),
    ('Mystery Guest', 'Unknown Test'),
    ('Mystery Guest', 'Verbose Test'),
    ('Print Statement', 'Redundant Assertion'),
    ('Print Statement', 'Resource Optimism'),
    ('Print Statement', 'Sensitive Equality'),
    ('Print Statement', 'Sleepy Test'),
    ('Print Statement', 'Unknown Test'),
    ('Print Statement', 'Verbose Test'),
    ('Redundant Assertion', 'Resource Optimism'),
    ('Redundant Assertion', 'Sensitive Equality'),
    ('Redundant Assertion', 'Sleepy Test'),
    ('Redundant Assertion', 'Unknown Test'),
    ('Redundant Assertion', 'Verbose Test'),
    ('Resource Optimism', 'Sensitive Equality'),
    ('Resource Optimism', 'Sleepy Test'),
    ('Resource Optimism', 'Unknown Test'),
    ('Resource Optimism', 'Verbose Test'),
    ('Sensitive Equality', 'Sleepy Test'),
    ('Sensitive Equality', 'Unknown Test'),
    ('Sensitive Equality', 'Verbose Test'),
    ('Sleepy Test', 'Unknown Test'),
    ('Sleepy Test', 'Verbose Test'),
    ('Unknown Test', 'Verbose Test')
]

test_smells_dir = os.path.join(os.getenv("HOME"), "Desktop", "testsmells_cooccurrence")
test_smells_input_dir = os.path.join(test_smells_dir, "testsmells_results")

for file_name in all_files:
    input_file = os.path.join(test_smells_input_dir, file_name)
    output_file = os.path.join(test_smells_dir, "couples_classes", file_name.replace('_result_bytestsmells.csv', '_couples_classes.csv'))

    test_smells = read_csv(input_file)
    cooccurrences = count_cooccurrences(test_smells, smell_pairs)
    save_results(cooccurrences, output_file)
