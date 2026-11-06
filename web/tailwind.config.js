/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        mastek: {
          navy: '#002180',
          dark: '#001554',
          light: '#EEF4FF',
          accent: '#1D4ED8',
        },
      },
    },
  },
  plugins: [],
};
