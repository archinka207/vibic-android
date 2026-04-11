import os
import argparse
from pathlib import Path

# Расширения файлов, которые обычно нужны для Kotlin проекта
KOTLIN_EXTENSIONS = {'.kt', '.kts'}
CONFIG_EXTENSIONS = {'.gradle', '.gradle.kts', '.xml', '.properties', '.json'}
RESOURCE_EXTENSIONS = {'.xml'}  # Для ресурсов Android

# Директории, которые нужно игнорировать
IGNORE_DIRS = {
    'build', '.gradle', 'idea', '.idea', '.git', 
    'gradle', 'captures', '.cxx', 'bin', 'obj',
    'node_modules', 'target', '.settings', '.vscode'
}

# Файлы, которые нужно игнорировать
IGNORE_FILES = {
    '*.iml', 'local.properties', 'gradlew', 'gradlew.bat',
    '*.log', '*.lock', '*.class', '*.jar', '*.apk'
}

def should_ignore(path: Path, ignore_dirs: set, ignore_patterns: list) -> bool:
    """Проверяет, нужно ли игнорировать файл или директорию"""
    
    # Проверка на игнорируемые директории
    for part in path.parts:
        if part in ignore_dirs:
            return True
    
    # Проверка на игнорируемые файлы по паттерну
    for pattern in ignore_patterns:
        if path.match(pattern):
            return True
    
    return False

def collect_project_files(project_path: str, include_resources: bool = True) -> list:
    """Собирает все файлы проекта"""
    files_list = []
    ignore_patterns = [f"*{pattern}" if not pattern.startswith('*') else pattern 
                      for pattern in IGNORE_FILES]
    
    for root, dirs, files in os.walk(project_path):
        # Удаляем игнорируемые директории из обхода
        dirs[:] = [d for d in dirs if not should_ignore(Path(root) / d, IGNORE_DIRS, [])]
        
        for file in files:
            file_path = Path(root) / file
            
            # Пропускаем игнорируемые файлы
            if should_ignore(file_path, IGNORE_DIRS, ignore_patterns):
                continue
            
            # Проверяем расширение файла
            ext = file_path.suffix.lower()
            
            # Всегда включаем Kotlin файлы
            if ext in KOTLIN_EXTENSIONS:
                files_list.append(file_path)
            # Включаем конфигурационные файлы
            elif ext in CONFIG_EXTENSIONS:
                files_list.append(file_path)
            # Включаем ресурсы, если нужно
            elif include_resources and ext in RESOURCE_EXTENSIONS:
                files_list.append(file_path)
            # Включаем README и другие текстовые файлы
            elif file.lower() in {'readme.md', 'readme', 'changelog.md', 'contributing.md'}:
                files_list.append(file_path)
    
    return sorted(files_list)

def create_context_file(files_list: list, output_file: str, project_name: str = None):
    """Создает единый файл с контекстом проекта"""
    
    with open(output_file, 'w', encoding='utf-8') as outfile:
        # Заголовок
        outfile.write("=" * 80 + "\n")
        outfile.write(f"КОНТЕКСТ ПРОЕКТА: {project_name if project_name else 'Kotlin Android Project'}\n")
        outfile.write("=" * 80 + "\n\n")
        
        # Содержание
        outfile.write("СОДЕРЖАНИЕ:\n")
        outfile.write("-" * 40 + "\n")
        for i, file_path in enumerate(files_list, 1):
            outfile.write(f"{i}. {file_path}\n")
        outfile.write("\n" + "=" * 80 + "\n\n")
        
        # Содержимое файлов
        for file_path in files_list:
            try:
                # Пытаемся прочитать файл с разными кодировками
                content = None
                encodings = ['utf-8', 'windows-1251', 'cp1251']
                
                for encoding in encodings:
                    try:
                        with open(file_path, 'r', encoding=encoding) as infile:
                            content = infile.read()
                        break
                    except UnicodeDecodeError:
                        continue
                
                if content is None:
                    # Если не удалось прочитать, пропускаем бинарные файлы
                    continue
                
                # Записываем информацию о файле
                outfile.write(f"\n{'=' * 80}\n")
                outfile.write(f"ФАЙЛ: {file_path}\n")
                outfile.write(f"{'=' * 80}\n")
                
                # Определяем язык для пометки
                if file_path.suffix == '.kt':
                    lang = "kotlin"
                elif file_path.suffix in {'.gradle', '.gradle.kts'}:
                    lang = "gradle"
                elif file_path.suffix == '.xml':
                    lang = "xml"
                elif file_path.suffix == '.json':
                    lang = "json"
                elif file_path.suffix == '.properties':
                    lang = "properties"
                else:
                    lang = "text"
                
                outfile.write(f"```{lang}\n")
                outfile.write(content)
                if not content.endswith('\n'):
                    outfile.write('\n')
                outfile.write("```\n\n")
                
            except Exception as e:
                outfile.write(f"\n{'=' * 80}\n")
                outfile.write(f"ФАЙЛ: {file_path} (ОШИБКА ЧТЕНИЯ)\n")
                outfile.write(f"{'=' * 80}\n")
                outfile.write(f"Не удалось прочитать файл: {str(e)}\n\n")
    
    print(f"\n✅ Файл контекста создан: {output_file}")
    print(f"📊 Всего обработано файлов: {len(files_list)}")

def main():
    parser = argparse.ArgumentParser(description='Сбор контекста Kotlin Android проекта в один файл')
    parser.add_argument('project_path', help='Путь к корневой директории проекта')
    parser.add_argument('-o', '--output', default='project_context.txt', 
                       help='Имя выходного файла (по умолчанию: project_context.txt)')
    parser.add_argument('-n', '--name', help='Название проекта (для заголовка)')
    parser.add_argument('--no-resources', action='store_true', 
                       help='Не включать файлы ресурсов (xml)')
    
    args = parser.parse_args()
    
    # Проверяем существование пути
    if not os.path.exists(args.project_path):
        print(f"❌ Ошибка: Путь {args.project_path} не существует")
        return
    
    print(f"🔍 Сканируем проект: {args.project_path}")
    print("⏳ Собираем файлы...")
    
    # Собираем файлы
    files_list = collect_project_files(args.project_path, not args.no_resources)
    
    if not files_list:
        print("❌ Не найдено файлов для обработки")
        return
    
    print(f"📁 Найдено файлов: {len(files_list)}")
    print("📝 Создаем контекстный файл...")
    
    # Создаем контекстный файл
    create_context_file(files_list, args.output, args.name or Path(args.project_path).name)

if __name__ == "__main__":
    main()
