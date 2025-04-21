import os
import re

PROJECTS_DIR = os.path.join(os.getenv("HOME"), "Desktop", "testsmells_cooccurrence", "projects")

def download_projects():
    with open("git_refs.csv") as f:
        for ref in f:
            name, link = ref.split(",")
            url = link.replace('tree', 'archive').replace('commit', 'archive').replace("\n", "").strip() + ".zip"
            print(url)
            os.system(f"cd {PROJECTS_DIR};curl -L -o {name}.zip {url}")
            os.system(f"cd {PROJECTS_DIR};unzip {name}.zip -d {name}; rm {name}.zip")

def count_test_methods(project_dir):
    testcase_class_pattern = re.compile(r'class\s+\w+\s+extends\s+TestCase')
    junit3_method_pattern = re.compile(
        r'(?:@Test\s*)?'                     
        r'^\s*public\s+void\s+(\w+)\s*\([^)]*\)',
        re.MULTILINE
    )

    junit45_method_pattern = re.compile(
        r'@Test\s*'                              
        r'(?:@\w+(?:\([^)]*\))?\s*)*'            
        r'(?:public\s+)?'                        
        r'(?!static)'                            
        r'void\s+\w+\s*'                         
        r'\([^)]*\)',                            
        re.MULTILINE
    )

    total_junit3 = 0
    total_junit45 = 0

    for root, _, files in os.walk(project_dir):
        for file in files:
            if file.endswith(".java"):
                file_path = os.path.join(root, file)
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()

                    junit45_matches = junit45_method_pattern.findall(content)
                    total_junit45 += len(junit45_matches)

                    if testcase_class_pattern.search(content):
                        methods = junit3_method_pattern.findall(content)
                        for method in methods:
                            method_pattern = r'static\s+void\s+' + re.escape(method)
                            if not re.search(method_pattern, content):
                                total_junit3 += 1

    total = total_junit3 + total_junit45
    return total


def analyze_java_project(project_path):
    java_files = []
    total_lines = 0
    test_classes = 0
    test_class_files = []

    # Walk through the directory recursively
    for root, _, files in os.walk(project_path):
        for file in files:
            if file.endswith(".java"):
                file_path = os.path.join(root, file)
                java_files.append(file_path)
                
                # Count lines in the file
                with open(file_path, 'r', encoding='utf-8') as f:
                    lines = f.readlines()
                    total_lines += len(lines)
                    
                    # Check if it's a test class by looking for @Test annotation or extends TestCase
                    is_test_class = False
                    for line in lines:
                        if '@Test' in line or 'extends TestCase' in line:
                            is_test_class = True
                            break
                    if is_test_class:
                        test_classes += 1
                        test_class_files.append(file_path)


    total_test_methods = count_test_methods(project_path)

    print(f"Total Java files: {len(java_files)}")
    print(f"Total lines of code: {total_lines}")
    print(f"Total test classes: {test_classes}")
    print(f"Total test methods: {total_test_methods}")
    return f"{len(java_files)},{total_lines},{test_classes},{total_test_methods}"


output = ["Project,Java Files,Total Lines,Total Test Classes,Total Test Methods\n"]
for project in os.listdir(PROJECTS_DIR):
    project_path = os.path.join(PROJECTS_DIR, project)
    if os.path.isdir(project_path):
        print(f"Analyzing project {project}...")
        result = analyze_java_project(project_path)
        output.append(f"{project},{result}\n")
        print("\n\n")

with open("Result_CountTestClassesAndMethods.csv", "w") as f:
    for line in output:
         f.write(line)
