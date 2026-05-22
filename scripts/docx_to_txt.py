import sys
import argparse
import zipfile
import xml.etree.ElementTree as ET

def get_docx_text(path):
    """
    Reads the main XML content of a docx file and extracts text paragraph by paragraph.
    Uses standard library libraries to avoid external dependency requirements.
    """
    namespaces = {
        'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
    }
    text = []
    with zipfile.ZipFile(path) as z:
        xml_content = z.read('word/document.xml')
        root = ET.fromstring(xml_content)
        # Search for paragraph elements and text elements in order
        for paragraph in root.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p'):
            p_text = []
            for run in paragraph.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}r'):
                for text_node in run.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}t'):
                    if text_node.text:
                        p_text.append(text_node.text)
            text.append(''.join(p_text))
    return '\n'.join(text)

def main():
    parser = argparse.ArgumentParser(description="Convert a .docx file to a plain .txt file using UTF-8 encoding.")
    parser.add_argument("input_file", help="Path to the input .docx file")
    parser.add_argument("output_file", help="Path to the output .txt file to write")
    args = parser.parse_args()

    try:
        text = get_docx_text(args.input_file)
        with open(args.output_file, 'w', encoding='utf-8') as f:
            f.write(text)
        print(f"Successfully converted '{args.input_file}' to '{args.output_file}'")
    except Exception as e:
        print(f"Error converting docx to txt: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    main()
