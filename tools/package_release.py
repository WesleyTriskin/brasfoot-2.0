import os
import sys
import shutil
import zipfile

def package():
    base_dir = os.path.abspath(r'C:\Brasfoot22-23')
    desktop_dir = os.path.abspath(r'C:\Users\Matheus Santana\Desktop')
    
    zip_name = 'brasfoot_release_betav1.zip'
    dest_desktop = os.path.join(desktop_dir, zip_name)
    dest_workspace = os.path.join(base_dir, zip_name)
    
    root_folder = 'Brasfoot 2026'
    
    single_files = [
        'Brasfoot2026.exe',
        'brasfoot.jar',
        'brasfoot-agent.jar',
        'brasfoot.ico',
        'options.bcf',
        'RELEASE_NOTES.md'
    ]
    
    directories = [
        'conf_estadual',
        'conf_ligas_nacionais',
        'img',
        'sav',
        'selecoes',
        'sons',
        'teams',
        'trofeus'
    ]
    
    print(f'Creating zip package: {dest_workspace} ...')
    
    total_files = 0
    with zipfile.ZipFile(dest_workspace, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        # Add single files
        for fname in single_files:
            fpath = os.path.join(base_dir, fname)
            if not os.path.exists(fpath):
                print(f'ERROR: Missing required file {fpath}')
                sys.exit(1)
            arcname = f'{root_folder}/{fname}'
            z.write(fpath, arcname)
            total_files += 1
            print(f'Added: {arcname}')
            
        # Add directories
        for dname in directories:
            dpath = os.path.join(base_dir, dname)
            if not os.path.exists(dpath):
                print(f'ERROR: Missing required directory {dpath}')
                sys.exit(1)
            for root, dirs, files in os.walk(dpath):
                for f in files:
                    ext = os.path.splitext(f)[1].lower()
                    if ext in ('.tmp', '.log', '.bak'):
                        continue
                    full_p = os.path.join(root, f)
                    rel_p = os.path.relpath(full_p, base_dir).replace('\\', '/')
                    arcname = f'{root_folder}/{rel_p}'
                    z.write(full_p, arcname)
                    total_files += 1

    print(f'Zip created successfully with {total_files} files.')
    size_mb = os.path.getsize(dest_workspace) / (1024 * 1024)
    print(f'Workspace Zip Size: {size_mb:.2f} MB')
    
    print(f'Copying to Desktop: {dest_desktop} ...')
    shutil.copy2(dest_workspace, dest_desktop)
    desktop_size_mb = os.path.getsize(dest_desktop) / (1024 * 1024)
    print(f'Desktop Zip Size: {desktop_size_mb:.2f} MB')
    
    # Validation test
    print('Testing zip integrity...')
    with zipfile.ZipFile(dest_desktop, 'r') as z_test:
        bad_crc = z_test.testzip()
        if bad_crc is not None:
            print(f'CRC check failed for {bad_crc}')
            sys.exit(1)
        print('Zip CRC verification passed!')

if __name__ == '__main__':
    package()
