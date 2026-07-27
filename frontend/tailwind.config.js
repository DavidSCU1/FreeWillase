/** @type {import('tailwindcss').Config} */

export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,ts,vue}"],
  theme: {
    container: {
      center: true,
      padding: '2rem',
    },
    extend: {
      colors: {
        apple: {
          blue: 'rgb(var(--apple-blue-rgb) / <alpha-value>)',
          green: 'rgb(var(--apple-green-rgb) / <alpha-value>)',
          gray: 'rgb(var(--apple-secondary-text-rgb) / <alpha-value>)',
          text: 'rgb(var(--apple-text-rgb) / <alpha-value>)',
          'secondary-text': 'rgb(var(--apple-secondary-text-rgb) / <alpha-value>)',
          border: 'rgb(var(--apple-border-rgb) / <alpha-value>)',
          background: 'rgb(var(--apple-background-rgb) / <alpha-value>)',
          card: 'rgb(var(--apple-card-rgb) / <alpha-value>)',
          surface: 'rgb(var(--apple-surface-rgb) / <alpha-value>)',
          'light-gray': 'rgb(var(--apple-surface-rgb) / <alpha-value>)',
          'dark-gray': 'rgb(var(--apple-dark-gray-rgb) / <alpha-value>)',
        }
      },
      borderRadius: {
        'apple': '12px',
        'apple-lg': '16px',
        'apple-xl': '24px',
      },
      boxShadow: {
        'apple': '0 16px 38px -24px rgba(9, 9, 11, 0.26), 0 10px 24px -18px rgba(14, 165, 233, 0.12)',
        'apple-hover': '0 22px 54px -28px rgba(9, 9, 11, 0.3), 0 16px 34px -24px rgba(45, 212, 191, 0.11)',
        'glass': '0 24px 72px -48px rgba(9, 9, 11, 0.4)',
      },
      fontFamily: {
        sans: [
          'SF Pro Display',
          'SF Pro Icons',
          'PingFang SC',
          'Helvetica Neue',
          'Helvetica',
          'Arial',
          'sans-serif',
        ],
      },
    },
  },
  plugins: [],
};
