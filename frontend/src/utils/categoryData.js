// Product category data (inspired by Xianyu)
export const categories = [
  {
    value: 'electronics',
    label: '📱 Electronics',
    icon: '📱',
    subcategories: [
      { value: 'phone', label: 'Phones' },
      { value: 'computer', label: 'Computers/Tablets' },
      { value: 'camera', label: 'Cameras/Photography' },
      { value: 'gaming', label: 'Gaming' },
      { value: 'accessories', label: 'Electronics Accessories' }
    ]
  },
  {
    value: 'clothing',
    label: '👔 Fashion & Apparel',
    icon: '👔',
    subcategories: [
      { value: 'men-clothing', label: "Men's Clothing" },
      { value: 'women-clothing', label: "Women's Clothing" },
      { value: 'shoes', label: 'Shoes' },
      { value: 'bags', label: 'Bags & Luggage' },
      { value: 'accessories', label: 'Accessories' }
    ]
  },
  {
    value: 'books',
    label: '📚 Books & Media',
    icon: '📚',
    subcategories: [
      { value: 'textbook', label: 'Textbooks' },
      { value: 'literature', label: 'Literature & Fiction' },
      { value: 'professional', label: 'Professional Books' },
      { value: 'magazine', label: 'Magazines & Journals' },
      { value: 'music', label: 'Music & Film' }
    ]
  },
  {
    value: 'beauty',
    label: '💄 Beauty & Personal Care',
    icon: '💄',
    subcategories: [
      { value: 'skincare', label: 'Skincare' },
      { value: 'makeup', label: 'Makeup' },
      { value: 'perfume', label: 'Fragrance' },
      { value: 'personal-care', label: 'Personal Care' }
    ]
  },
  {
    value: 'home',
    label: '🏠 Home & Living',
    icon: '🏠',
    subcategories: [
      { value: 'furniture', label: 'Furniture' },
      { value: 'decoration', label: 'Home Decor' },
      { value: 'kitchen', label: 'Kitchenware' },
      { value: 'bedding', label: 'Bedding' },
      { value: 'appliances', label: 'Home Appliances' }
    ]
  },
  {
    value: 'sports',
    label: '⚽ Sports & Outdoors',
    icon: '⚽',
    subcategories: [
      { value: 'fitness', label: 'Fitness Equipment' },
      { value: 'sports-wear', label: 'Sportswear' },
      { value: 'outdoor', label: 'Outdoor Gear' },
      { value: 'bicycle', label: 'Bicycles' }
    ]
  },
  {
    value: 'baby',
    label: '👶 Baby & Kids',
    icon: '👶',
    subcategories: [
      { value: 'toys', label: 'Toys' },
      { value: 'clothing', label: "Kids' Clothing" },
      { value: 'feeding', label: 'Feeding Supplies' },
      { value: 'stroller', label: 'Strollers & Seats' }
    ]
  },
  {
    value: 'food',
    label: '🍔 Food & Beverages',
    icon: '🍔',
    subcategories: [
      { value: 'snacks', label: 'Snacks & Specialty Foods' },
      { value: 'health-food', label: 'Health Supplements' },
      { value: 'tea', label: 'Tea' },
      { value: 'alcohol', label: 'Alcoholic Drinks' }
    ]
  },
  {
    value: 'jewelry',
    label: '💎 Jewelry & Accessories',
    icon: '💎',
    subcategories: [
      { value: 'necklace', label: 'Necklaces' },
      { value: 'ring', label: 'Rings' },
      { value: 'bracelet', label: 'Bracelets & Bangles' },
      { value: 'watch', label: 'Watches' }
    ]
  },
  {
    value: 'vehicles',
    label: '🚗 Vehicles',
    icon: '🚗',
    subcategories: [
      { value: 'car', label: 'Cars' },
      { value: 'motorcycle', label: 'Motorcycles' },
      { value: 'ebike', label: 'Electric Vehicles' },
      { value: 'parts', label: 'Parts & Accessories' }
    ]
  },
  {
    value: 'pets',
    label: '🐶 Pet Supplies',
    icon: '🐶',
    subcategories: [
      { value: 'food', label: 'Pet Food' },
      { value: 'supplies', label: 'Pet Supplies' },
      { value: 'toys', label: 'Pet Toys' }
    ]
  },
  {
    value: 'other',
    label: '🎁 Other Secondhand',
    icon: '🎁',
    subcategories: [
      { value: 'tickets', label: 'Tickets & Vouchers' },
      { value: 'cards', label: 'Gift Cards' },
      { value: 'collectibles', label: 'Collectibles' },
      { value: 'other', label: 'Other' }
    ]
  }
];

// Get all top-level categories
export const getMainCategories = () => {
  return categories.map(cat => ({
    value: cat.value,
    label: cat.label,
    icon: cat.icon
  }));
};

// Get subcategories by top-level category
export const getSubCategories = (mainCategory) => {
  const category = categories.find(cat => cat.value === mainCategory);
  return category ? category.subcategories : [];
};

// Get category name by value
export const getCategoryLabel = (categoryValue) => {
  for (const category of categories) {
    if (category.value === categoryValue) {
      return category.label;
    }
    const subCategory = category.subcategories.find(sub => sub.value === categoryValue);
    if (subCategory) {
      return `${category.label} / ${subCategory.label}`;
    }
  }
  return 'Uncategorized';
};

// Get category icon
export const getCategoryIcon = (categoryValue) => {
  const category = categories.find(cat => cat.value === categoryValue);
  return category ? category.icon : '🎁';
};

