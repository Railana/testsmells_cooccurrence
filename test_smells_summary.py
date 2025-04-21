import pandas as pd

file_path = '/Users/railanasantana/Desktop/New_testsmells_analysis/accumulo_result_bytestsmells.csv'
output_file = '/Users/railanasantana/Desktop/New_testsmells_analysis/21_abril_test_smells_summary.txt'

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
test_smell_counts = df['testSmellName'].value_counts()

# Preparing the summary headers
summary = {
    "Instances of test smells identified": instances,
    "Number of different types of test smells": unique_test_smells,
    "Number of different test classes": unique_classes,
    "Number of different test methods": len(unique_methods)
}

with open(output_file, 'w') as f:
    f.write("Summary:\n")
    for key, value in summary.items():
        f.write(f"{key}: {value}\n")

    f.write("\nInstances by test smells:\n")
    for smell, count in test_smell_counts.items():
        f.write(f"{smell}: {count}\n")

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
