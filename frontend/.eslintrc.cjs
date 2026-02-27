module.exports = {
    root: true,
    env: {
        browser: true,
        es2020: true,
    },
    parser: '@typescript-eslint/parser',
    parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
    },
    plugins: ['@typescript-eslint', 'react-hooks', 'react-refresh'],
    extends: [
        'eslint:recommended',
        'plugin:react-hooks/recommended',
    ],
    ignorePatterns: ['dist', 'node_modules'],
    rules: {
        'no-undef': 'off',
        'no-unused-vars': 'off',
        'no-prototype-builtins': 'off',
        'no-redeclare': 'off',
        'no-case-declarations': 'off',
        'no-constant-condition': 'off',
        'no-useless-escape': 'off',
        '@typescript-eslint/no-unused-vars': 'off',
        'react-hooks/exhaustive-deps': 'off',
        'react-refresh/only-export-components': 'off',
    },
};
