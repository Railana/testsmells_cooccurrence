import pandas as pd
import os

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

all_test_smells = [
        "Assertion Roulette",
        "Conditional Test Logic",
        "Constructor Initialization",
        "Duplicate Assert",
        "Eager Test",
        "EmptyTest",
        "Exception Catching Throwing",
        "General Fixture",
        "IgnoredTest",
        "Lazy Test",
        "Magic Number Test",
        "Mystery Guest",
        "Print Statement",
        "Redundant Assertion",
        "Resource Optimism",
        "Sensitive Equality",
        "Sleepy Test",
        "Unknown Test",
        "Verbose Test",
    ]

for file_name in all_files:

    #file_name = '01_accumulo_result_bytestsmells.csv'
    file_path = '~/Desktop/testsmells_cooccurrence/testsmells_results/' + file_name
    output_file = os.getenv('HOME') + '/Desktop/testsmells_cooccurrence/distribution/' + file_name.replace('_result_bytestsmells.csv', '_distribution.txt')

    # Reading CSV file
    df = pd.read_csv(file_path, delimiter=',')

    # Processing test methods to handle multiple methods separated by comma and space
    methods_list = df['testSmellMethod'].str.split(', ')
    all_methods = [method for sublist in methods_list for method in sublist]
    unique_methods = set(all_methods)

    # Extracting all lines as string
    def extract_lines(line_str):
        lines = set()
        parts = line_str.split(', ')
        for part in parts:
            if '-' in part:
                start, end = part.split('-')
                lines.update(range(int(start), int(end) + 1))
            else:
                lines.add(int(part))
        return lines

    def count_classes_by_test_smells(df):
        unique_classes = df[["testSmellName", "pathFile"]].drop_duplicates()
        distribution = (
            unique_classes.groupby("testSmellName")["pathFile"]
            .count()
            .reindex(all_test_smells, fill_value=0)
        )
        return distribution

    def count_method_by_test_smells(df):
        unique_methods = df[["testSmellName", "pathFile", "testSmellMethod"]].drop_duplicates()
        count_by_method = (
            unique_methods.groupby("testSmellName")["testSmellMethod"]
            .count()
            .reindex(all_test_smells, fill_value=0)
        )
        return count_by_method

    # Applying the function to extract rows and creating a new column with the set of rows
    df['lines'] = df['testSmellLineBegin'].apply(extract_lines)

    # Identifying test smells on the same lines and classes
    class_line_smells = {}
    for _, row in df.iterrows():
        class_name = row['pathFile']
        method_name = row['testSmellMethod']
        if class_name not in class_line_smells:
            class_line_smells[class_name] = {}
        for line in row['lines']:
            if line not in class_line_smells[class_name]:
                class_line_smells[class_name][line] = []
            class_line_smells[class_name][line].append((row['testSmellName'], method_name))

    # Summarizing data
    instances = len(df)
    unique_test_smells = df['testSmellName'].nunique()
    unique_classes = df['pathFile'].nunique()
    test_smells = df['testSmellName'].unique()
    test_classes = df['pathFile'].unique()

    # Counting instances by test smell types
    test_smell_counts = df['testSmellName'].value_counts().reindex(all_test_smells, fill_value=0)

    # Preparing the summary headers
    summary = {
        "Instances of test smells identified": instances,
        "Number of different types of test smells": unique_test_smells,
        "Number of different test classes": unique_classes,
        "Number of different test methods": len(unique_methods)
    }

    # Calculate distribution by classes
    distribution_classes = count_classes_by_test_smells(df)

    # Calculate distribution by methods
    distribution_methods = count_method_by_test_smells(df)

    with open(output_file, 'w') as f:
        f.write("Summary:\n")
        for key, value in summary.items():
            f.write(f"{key}: {value}\n")

        f.write("\nInstances by test smells:\n")
        for smell, count in test_smell_counts.items():
            f.write(f"{smell}: {count}\n")

        f.write("\nDistribution by Classes:\n")
        for row in distribution_classes.items():
            test_smell, count = row
            text = f"{test_smell}: {count}"
            f.write(f"{text}\n")
        
        f.write("\nDistribution by Methods:\n")
        for row in distribution_methods.items():
            test_smell, count = row
            text = f"{test_smell}: {count}"
            f.write(f"{text}\n")

        f.write("\nTest classes:\n")
        for test_class in test_classes:
            f.write(f"{test_class}\n")

        f.write("\nTest methods:\n")
        for method in unique_methods:
            f.write(f"{method}\n")

        f.write("\nTest smells on the same lines and classes (with more than one test smell):\n")
        for class_name, lines in class_line_smells.items():
            for line, smells in lines.items():
                if len(smells) > 1:
                    smells_methods = [f"{smell} (Method: {method})" for smell, method in smells]
                    f.write(f"Class {class_name} - Line {line}: {', '.join(smells_methods)}\n")

    print(f"File recorded in {output_file}")
